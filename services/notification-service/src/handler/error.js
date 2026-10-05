// An event that can never be handled, however often it is retried: it is
// dropped rather than requeued.
class UnprocessableEventError extends Error { }

module.exports = { UnprocessableEventError };
