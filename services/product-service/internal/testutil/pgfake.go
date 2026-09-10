// Package testutil holds the doubles the tests share.
package testutil

import (
	"context"
	"database/sql"
	"database/sql/driver"
	"io"
	"regexp"
	"sync"
	"testing"

	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

// FakeDB answers the statements gorm sends without a database behind it, so the
// repositories can be exercised against the real postgres dialect. Statements
// are routed by pattern rather than by order, a preload gorm decides to issue on
// its own therefore does not break the expectations of the test.
type FakeDB struct {
	t *testing.T

	mu         sync.Mutex
	handlers   []*handler
	statements []Statement
	commits    int
	rollbacks  int
}

// Statement is a statement the fake was asked to run, kept so a test can assert
// on the SQL a repository builds.
type Statement struct {
	SQL  string
	Args []driver.Value
}

// handler is the answer registered for the statements matching its pattern.
type handler struct {
	pattern  *regexp.Regexp
	rows     *RowSet
	affected int64
	err      error
	calls    int
}

// RowSet is a result set a handler replays, a fresh cursor is handed out on
// every match so one registration can answer repeated statements.
type RowSet struct {
	columns []string
	values  [][]driver.Value
}

// Rows starts a result set holding the given columns.
func Rows(columns ...string) *RowSet {
	return &RowSet{columns: columns}
}

// Add appends a row, the values are positional to the columns of the set.
func (r *RowSet) Add(values ...driver.Value) *RowSet {
	r.values = append(r.values, values)

	return r
}

// NewDB builds a gorm handle backed by the fake, together with the fake itself
// so the test can register answers and read back the statements that ran.
func NewDB(t *testing.T) (*gorm.DB, *FakeDB) {
	t.Helper()

	fake := &FakeDB{t: t}
	orm, err := gorm.Open(postgres.New(postgres.Config{Conn: sql.OpenDB(fake)}), &gorm.Config{
		Logger: logger.Discard,
	})
	if err != nil {
		t.Fatalf("open the fake gorm handle: %v", err)
	}

	return orm, fake
}

// Query registers the rows returned for every statement matching pattern.
func (f *FakeDB) Query(pattern string, rows *RowSet) *FakeDB {
	return f.register(&handler{pattern: regexp.MustCompile(pattern), rows: rows})
}

// Exec registers the number of affected rows returned for every statement
// matching pattern.
func (f *FakeDB) Exec(pattern string, affected int64) *FakeDB {
	return f.register(&handler{pattern: regexp.MustCompile(pattern), affected: affected})
}

// Fail registers the error returned for every statement matching pattern.
func (f *FakeDB) Fail(pattern string, err error) *FakeDB {
	return f.register(&handler{pattern: regexp.MustCompile(pattern), err: err})
}

func (f *FakeDB) register(h *handler) *FakeDB {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.handlers = append(f.handlers, h)

	return f
}

// Statements returns every statement the fake was asked to run, in order.
func (f *FakeDB) Statements() []Statement {
	f.mu.Lock()
	defer f.mu.Unlock()

	return append([]Statement(nil), f.statements...)
}

// Calls counts the statements that matched the pattern.
func (f *FakeDB) Calls(pattern string) int {
	matcher := regexp.MustCompile(pattern)

	f.mu.Lock()
	defer f.mu.Unlock()

	count := 0
	for _, statement := range f.statements {
		if matcher.MatchString(statement.SQL) {
			count++
		}
	}

	return count
}

// Commits counts the transactions that were committed.
func (f *FakeDB) Commits() int {
	f.mu.Lock()
	defer f.mu.Unlock()

	return f.commits
}

// Rollbacks counts the transactions that were rolled back.
func (f *FakeDB) Rollbacks() int {
	f.mu.Lock()
	defer f.mu.Unlock()

	return f.rollbacks
}

// match records the statement and returns the handler answering it, a statement
// no handler claims is answered with an empty result.
func (f *FakeDB) match(query string, args []driver.NamedValue) *handler {
	values := make([]driver.Value, 0, len(args))
	for _, arg := range args {
		values = append(values, arg.Value)
	}

	f.mu.Lock()
	defer f.mu.Unlock()
	f.statements = append(f.statements, Statement{SQL: query, Args: values})

	for _, h := range f.handlers {
		if h.pattern.MatchString(query) {
			h.calls++

			return h
		}
	}

	return nil
}

// Connect implements driver.Connector.
func (f *FakeDB) Connect(context.Context) (driver.Conn, error) {
	return &fakeConn{db: f}, nil
}

// Driver implements driver.Connector.
func (f *FakeDB) Driver() driver.Driver {
	return fakeDriver{db: f}
}

type fakeDriver struct {
	db *FakeDB
}

func (d fakeDriver) Open(string) (driver.Conn, error) {
	return &fakeConn{db: d.db}, nil
}

type fakeConn struct {
	db *FakeDB
}

func (c *fakeConn) Prepare(query string) (driver.Stmt, error) {
	return &fakeStmt{conn: c, query: query}, nil
}

func (c *fakeConn) PrepareContext(_ context.Context, query string) (driver.Stmt, error) {
	return c.Prepare(query)
}

func (c *fakeConn) Close() error {
	return nil
}

func (c *fakeConn) Ping(context.Context) error {
	return nil
}

func (c *fakeConn) Begin() (driver.Tx, error) {
	return &fakeTx{db: c.db}, nil
}

func (c *fakeConn) BeginTx(context.Context, driver.TxOptions) (driver.Tx, error) {
	return c.Begin()
}

func (c *fakeConn) QueryContext(_ context.Context, query string, args []driver.NamedValue) (driver.Rows, error) {
	h := c.db.match(query, args)
	if h == nil {
		return &fakeRows{}, nil
	}

	if h.err != nil {
		return nil, h.err
	}

	if h.rows == nil {
		return &fakeRows{}, nil
	}

	return &fakeRows{columns: h.rows.columns, values: h.rows.values}, nil
}

func (c *fakeConn) ExecContext(_ context.Context, query string, args []driver.NamedValue) (driver.Result, error) {
	h := c.db.match(query, args)
	if h == nil {
		return fakeResult{}, nil
	}

	if h.err != nil {
		return nil, h.err
	}

	return fakeResult{affected: h.affected}, nil
}

type fakeStmt struct {
	conn  *fakeConn
	query string
}

func (s *fakeStmt) Close() error {
	return nil
}

func (s *fakeStmt) NumInput() int {
	return -1
}

func (s *fakeStmt) Exec(args []driver.Value) (driver.Result, error) {
	return s.conn.ExecContext(context.Background(), s.query, named(args))
}

func (s *fakeStmt) Query(args []driver.Value) (driver.Rows, error) {
	return s.conn.QueryContext(context.Background(), s.query, named(args))
}

func named(args []driver.Value) []driver.NamedValue {
	values := make([]driver.NamedValue, 0, len(args))
	for i, arg := range args {
		values = append(values, driver.NamedValue{Ordinal: i + 1, Value: arg})
	}

	return values
}

type fakeTx struct {
	db *FakeDB
}

func (t *fakeTx) Commit() error {
	t.db.mu.Lock()
	defer t.db.mu.Unlock()
	t.db.commits++

	return nil
}

func (t *fakeTx) Rollback() error {
	t.db.mu.Lock()
	defer t.db.mu.Unlock()
	t.db.rollbacks++

	return nil
}

type fakeRows struct {
	columns []string
	values  [][]driver.Value
	pos     int
}

func (r *fakeRows) Columns() []string {
	return r.columns
}

func (r *fakeRows) Close() error {
	return nil
}

func (r *fakeRows) Next(dest []driver.Value) error {
	if r.pos >= len(r.values) {
		return io.EOF
	}

	copy(dest, r.values[r.pos])
	r.pos++

	return nil
}

type fakeResult struct {
	affected int64
}

func (r fakeResult) LastInsertId() (int64, error) {
	return 0, nil
}

func (r fakeResult) RowsAffected() (int64, error) {
	return r.affected, nil
}
