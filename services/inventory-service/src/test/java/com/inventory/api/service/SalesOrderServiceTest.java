package com.inventory.api.service;

import com.inventory.api.enums.DocType;
import com.inventory.api.enums.SalesStatus;
import com.inventory.api.enums.StockActivity;
import com.inventory.api.exception.BadRequestException;
import com.inventory.api.exception.NotFoundException;
import com.inventory.api.model.dto.request.so.SOCancelReq;
import com.inventory.api.model.dto.request.so.SOSubmitReq;
import com.inventory.api.model.dto.response.Response;
import com.inventory.api.model.dto.response.so.SODetailRes;
import com.inventory.api.model.dto.response.so.SORes;
import com.inventory.api.model.entity.Product;
import com.inventory.api.model.entity.Refund;
import com.inventory.api.model.entity.SalesOrder;
import com.inventory.api.model.entity.SalesOrderDetail;
import com.inventory.api.model.entity.Stock;
import com.inventory.api.model.entity.StockPosition;
import com.inventory.api.repository.RefundRepository;
import com.inventory.api.repository.SalesOrderDetailRepository;
import com.inventory.api.repository.SalesOrderRepository;
import com.inventory.api.repository.StockPositionRepository;
import com.inventory.api.repository.StockRepository;
import com.inventory.api.service.impl.SalesOrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit tests for moving the stock of a sales order when it is paid, and back when it is cancelled. */
class SalesOrderServiceTest {

    private static final BigDecimal PRICE = new BigDecimal("1500");

    private SalesOrderRepository soRepository;
    private SalesOrderDetailRepository soDetailRepository;
    private StockRepository stockRepository;
    private StockPositionRepository stockPositionRepository;
    private RefundRepository refundRepository;

    private SalesOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        soRepository = mock(SalesOrderRepository.class);
        soDetailRepository = mock(SalesOrderDetailRepository.class);
        stockRepository = mock(StockRepository.class);
        stockPositionRepository = mock(StockPositionRepository.class);
        refundRepository = mock(RefundRepository.class);

        service = new SalesOrderServiceImpl(soRepository, soDetailRepository,
                stockRepository, stockPositionRepository, refundRepository);
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    private static Product product(Long id, String name, Integer onHand) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setSellPrice(PRICE);

        if (onHand != null) {
            StockPosition position = new StockPosition();
            position.setId(300L + id);
            position.setQuantity(onHand);
            // StockPosition.setProduct is deliberately left unset: the generated
            // equals/hashCode of these entities walk their associations, so a
            // Product <-> StockPosition cycle recurses until the stack runs out.
            // The service only reads product.getStockPosition() anyway.
            product.setStockPosition(position);
        }

        return product;
    }

    private static SalesOrderDetail detail(Long id, Product product, int quantity) {
        SalesOrderDetail detail = new SalesOrderDetail();
        detail.setId(id);
        detail.setProduct(product);
        detail.setQuantity(quantity);
        detail.setUnitPrice(PRICE);
        detail.setSubtotal(PRICE.multiply(BigDecimal.valueOf(quantity)));

        return detail;
    }

    private static SalesOrder storedOrder(SalesOrderDetail... details) {
        SalesOrder so = new SalesOrder();
        so.setId(90L);
        so.setDocumentNumber("SO/2026/09/0001");
        so.setStatus(SalesStatus.PAID);
        so.setSalesOrderDetails(new ArrayList<>(List.of(details)));

        return so;
    }

    /** Stores the order where the service reads it from, under its row lock. */
    private SalesOrder given(SalesOrder so) {
        when(soRepository.lockById(so.getId())).thenReturn(Optional.of(so));
        when(soDetailRepository.findProductIds(so.getId())).thenReturn(so.getSalesOrderDetails().stream()
                .map(detail -> detail.getProduct().getId()).distinct().toList());
        return so;
    }

    private static SOSubmitReq request(Long id) {
        SOSubmitReq req = new SOSubmitReq();
        req.setId(id);

        return req;
    }

    @SuppressWarnings("unchecked")
    private List<Stock> savedStocks() {
        ArgumentCaptor<List<Stock>> captor = ArgumentCaptor.forClass(List.class);
        verify(stockRepository).saveAll(captor.capture());

        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<StockPosition> savedPositions() {
        ArgumentCaptor<Iterable<StockPosition>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(stockPositionRepository).saveAll(captor.capture());

        List<StockPosition> positions = new ArrayList<>();
        captor.getValue().forEach(positions::add);

        return positions;
    }

    // ------------------------------------------------------------------
    // doSubmit -- stock movements
    // ------------------------------------------------------------------

    @Test
    void shouldRecordAStockOutMovementPerLine() {
        Product coffee = product(7L, "Kopi Robusta 1kg", 10);
        Product tea = product(8L, "Teh Hijau 500g", 10);
        SalesOrder stored = storedOrder(detail(80L, coffee, 4), detail(81L, tea, 2));
        given(stored);

        service.doSubmit(request(90L));

        List<Stock> movements = savedStocks();

        assertEquals(2, movements.size());
        assertEquals(StockActivity.SO, movements.get(0).getActivity(), "selling goods is a stock out");
        assertEquals(DocType.SO, movements.get(0).getDocumentType());
        assertEquals("SO/2026/09/0001", movements.get(0).getDocumentNumber());
        assertEquals(4, movements.get(0).getQuantity());
        assertSame(coffee, movements.get(0).getProduct());
        assertSame(stored, movements.get(0).getSalesOrder(), "the movement must point back at the document");
        assertEquals(2, movements.get(1).getQuantity());
        assertSame(tea, movements.get(1).getProduct());
    }

    @Test
    void shouldDeductTheStockPositionOfEachProduct() {
        Product coffee = product(7L, "Kopi Robusta 1kg", 10);
        StockPosition existing = coffee.getStockPosition();
        given(storedOrder(detail(80L, coffee, 4)));

        service.doSubmit(request(90L));

        List<StockPosition> positions = savedPositions();

        assertEquals(1, positions.size());
        assertSame(existing, positions.get(0), "the existing row must be updated, not replaced");
        assertEquals(6, positions.get(0).getQuantity());
    }

    @Test
    void shouldDeductOnceForAProductRepeatedAcrossLines() {
        Product coffee = product(7L, "Kopi Robusta 1kg", 10);
        given(storedOrder(detail(80L, coffee, 4), detail(81L, coffee, 6)));

        service.doSubmit(request(90L));

        List<StockPosition> positions = savedPositions();

        // One row holding the remainder, not two rows racing each other.
        assertEquals(1, positions.size());
        assertEquals(0, positions.get(0).getQuantity());
    }

    @Test
    void shouldAllowSellingTheLastOfTheStock() {
        Product coffee = product(7L, "Kopi Robusta 1kg", 4);
        given(storedOrder(detail(80L, coffee, 4)));

        service.doSubmit(request(90L));

        assertEquals(0, savedPositions().get(0).getQuantity(), "an exact match is not an overdraw");
    }

    @Test
    void shouldRefuseToSellMoreThanIsHeld() {
        Product coffee = product(7L, "Kopi Robusta 1kg", 3);
        given(storedOrder(detail(80L, coffee, 4)));

        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> service.doSubmit(request(90L)));

        assertTrue(thrown.getMessage().contains("(4)"));
        assertTrue(thrown.getMessage().contains("(3)"));
        assertTrue(thrown.getMessage().contains("Kopi Robusta 1kg"));

        // Nothing may be written when one line of the order cannot be served.
        verify(stockRepository, never()).saveAll(anyList());
        verify(stockPositionRepository, never()).saveAll(any());
    }

    @Test
    void shouldRefuseAProductThatHasNeverBeenStocked() {
        Product coffee = product(7L, "Kopi Robusta 1kg", null);
        given(storedOrder(detail(80L, coffee, 1)));

        // A product with no position row counts as zero on hand rather than as
        // unlimited, so the order is refused instead of driving stock negative.
        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> service.doSubmit(request(90L)));

        assertTrue(thrown.getMessage().contains("(0)"));
        verify(stockRepository, never()).saveAll(anyList());
    }

    @Test
    void shouldCountTheSecondLineAgainstWhatTheFirstOneLeft() {
        Product coffee = product(7L, "Kopi Robusta 1kg", 5);
        given(storedOrder(detail(80L, coffee, 4), detail(81L, coffee, 2)));

        // Each line alone fits; together they do not, which is only visible
        // because the running remainder is carried between lines.
        assertThrows(BadRequestException.class, () -> service.doSubmit(request(90L)));

        verify(stockRepository, never()).saveAll(anyList());
    }

    // ------------------------------------------------------------------
    // doSubmit -- response
    // ------------------------------------------------------------------

    @Test
    void shouldReturnTheOrderWithItsLines() {
        Product coffee = product(7L, "Kopi Robusta 1kg", 10);
        given(storedOrder(detail(80L, coffee, 4)));

        Response response = service.doSubmit(request(90L));

        assertEquals(200, response.getCode());
        assertEquals("Success", response.getStatus());

        SORes res = (SORes) response.getData();

        assertNotNull(res);
        assertEquals("SO/2026/09/0001", res.getDocumentNumber());
        assertEquals(1, res.getDetails().size());

        SODetailRes line = res.getDetails().get(0);

        // The product is flattened to an id and a name: the caller of this flow is
        // a message producer, not a client that can follow an association.
        assertEquals(7L, line.getProductId());
        assertEquals("Kopi Robusta 1kg", line.getProductName());
        assertEquals(4, line.getQuantity());
        assertEquals(PRICE, line.getUnitPrice());
        assertEquals(new BigDecimal("6000"), line.getSubtotal());
    }

    @Test
    void shouldReadTheOrderByTheIdInTheRequest() {
        Product coffee = product(7L, "Kopi Robusta 1kg", 10);
        given(storedOrder(detail(80L, coffee, 1)));

        service.doSubmit(request(90L));

        verify(soRepository).lockById(90L);
        // Submitting reads and moves stock; it does not rewrite the order or its
        // lines, which the order service owns.
        verify(soRepository, never()).save(any(SalesOrder.class));
        verify(soDetailRepository, never()).saveAll(anyList());
    }

    @Test
    void shouldLockTheStockPositionsBeforeMovingThem() {
        Product coffee = product(7L, "Kopi Robusta 1kg", 10);
        Product tea = product(8L, "Teh Hijau 500g", 10);
        given(storedOrder(detail(80L, coffee, 4), detail(81L, tea, 2)));

        service.doSubmit(request(90L));

        // Otherwise a cancel of another order, on its own queue, could move the same
        // product at once and one of the two writes would be lost.
        InOrder inOrder = inOrder(soRepository, stockPositionRepository);
        inOrder.verify(soRepository).lockById(90L);
        inOrder.verify(stockPositionRepository).lockByProductIds(List.of(7L, 8L));
        inOrder.verify(stockPositionRepository).saveAll(any());
    }

    @Test
    void shouldRefuseAnOrderThatDoesNotExist() {
        when(soRepository.lockById(90L)).thenReturn(Optional.empty());

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.doSubmit(request(90L)));

        assertEquals("Data Sales Order with id 90 not found", thrown.getMessage());
        verifyNoInteractions(stockRepository, stockPositionRepository);
    }

    // ------------------------------------------------------------------
    // doSubmit -- delivered twice, or after a cancel
    // ------------------------------------------------------------------

    @Test
    void shouldTakeTheStockOnlyOnceWhenTheSubmitIsDeliveredAgain() {
        Product coffee = product(7L, "Kopi Robusta 1kg", 10);
        given(storedOrder(detail(80L, coffee, 4)));
        when(stockRepository.existsBySalesOrderIdAndActivity(90L, StockActivity.SO)).thenReturn(true);

        Response response = service.doSubmit(request(90L));

        assertEquals(200, response.getCode());
        verify(stockRepository, never()).saveAll(anyList());
        verifyNoInteractions(stockPositionRepository);
        assertEquals(10, coffee.getStockPosition().getQuantity());
    }

    @Test
    void shouldNotTakeTheStockOfAnOrderCancelledBeforeTheSubmitArrived() {
        Product coffee = product(7L, "Kopi Robusta 1kg", 10);
        SalesOrder so = given(storedOrder(detail(80L, coffee, 4)));
        so.setStatus(SalesStatus.CANCELLED);

        Response response = service.doSubmit(request(90L));

        // Not an error: the cancel won the race and has nothing to put back either.
        assertEquals(200, response.getCode());
        verify(stockRepository, never()).saveAll(anyList());
        verifyNoInteractions(stockPositionRepository);
    }

    @ParameterizedTest
    @EnumSource(value = SalesStatus.class, names = {"PAID", "CANCELLED"}, mode = EnumSource.Mode.EXCLUDE)
    void shouldRefuseAnOrderThatIsNotPaid(SalesStatus status) {
        Product coffee = product(7L, "Kopi Robusta 1kg", 10);
        given(storedOrder(detail(80L, coffee, 4))).setStatus(status);

        BadRequestException thrown = assertThrows(BadRequestException.class, () -> service.doSubmit(request(90L)));

        assertEquals("Sales order SO/2026/09/0001 is not paid", thrown.getMessage());
        verify(stockRepository, never()).saveAll(anyList());
        verifyNoInteractions(stockPositionRepository);
    }

    // ------------------------------------------------------------------
    // doCancel
    // ------------------------------------------------------------------

    private static final String REFUND_NO = "RF20260923001";

    private static Refund refund(Long id, String documentNumber, String amount) {
        Refund refund = new Refund();
        refund.setId(id);
        refund.setDocumentNumber(documentNumber);
        refund.setAmount(amount == null ? null : new BigDecimal(amount));

        return refund;
    }

    private SOCancelReq cancel(Long id) {
        return cancel(id, refund(501L, REFUND_NO, "9000"));
    }

    /** A cancel naming these refunds, which the order service has recorded for the order. */
    private SOCancelReq cancel(Long id, Refund... refunds) {
        when(refundRepository.findBySalesOrderIdAndIdInOrderByIdAsc(eq(id), anyCollection()))
                .thenReturn(List.of(refunds));
        return cancelWithIds(id, Arrays.stream(refunds).map(Refund::getId).toList());
    }

    private static SOCancelReq cancelWithIds(Long id, List<Long> refundIds) {
        SOCancelReq req = new SOCancelReq();
        req.setId(id);
        req.setRefundIds(refundIds);

        return req;
    }

    private static void assertMovement(Stock stock, Refund refund, Product product, int quantity) {
        assertEquals(StockActivity.SI, stock.getActivity());
        assertEquals(DocType.SR, stock.getDocumentType());
        assertEquals(refund.getDocumentNumber(), stock.getDocumentNumber());
        assertSame(refund, stock.getSalesRefund(), "the movement must point back at its refund");
        assertSame(product, stock.getProduct());
        assertEquals(quantity, stock.getQuantity());
    }

    private static Stock stockOut(SalesOrder so, Product product, int quantity) {
        Stock stock = new Stock();
        stock.setProduct(product);
        stock.setDocumentNumber(so.getDocumentNumber());
        stock.setDocumentType(DocType.SO);
        stock.setActivity(StockActivity.SO);
        stock.setQuantity(quantity);
        stock.setSalesOrder(so);

        return stock;
    }

    /** A cancelled order whose submit already took these quantities out. */
    private SalesOrder givenCancelledAfterSubmit(Product coffee, int coffeeQty, Product tea, int teaQty) {
        SalesOrder so = given(storedOrder(detail(80L, coffee, coffeeQty), detail(81L, tea, teaQty)));
        so.setStatus(SalesStatus.CANCELLED);
        when(stockRepository.findBySalesOrderIdAndActivityOrderByIdAsc(90L, StockActivity.SO))
                .thenReturn(List.of(stockOut(so, coffee, coffeeQty), stockOut(so, tea, teaQty)));
        return so;
    }

    @Test
    void shouldRecordAStockInMovementForEveryStockOutOfTheOrder() {
        Product coffee = product(7L, "Kopi Robusta 1kg", 6);
        Product tea = product(8L, "Teh Hijau 500g", 8);
        SalesOrder so = givenCancelledAfterSubmit(coffee, 4, tea, 2);
        Refund refund = refund(501L, REFUND_NO, "9000");

        Response response = service.doCancel(cancel(90L, refund));

        assertEquals(200, response.getCode());
        List<Stock> movements = savedStocks();
        assertEquals(2, movements.size());
        assertEquals(StockActivity.SI, movements.get(0).getActivity(), "returned goods are a stock in");
        assertEquals(DocType.SR, movements.get(0).getDocumentType(), "returned goods are recorded as a refund");
        assertEquals(REFUND_NO, movements.get(0).getDocumentNumber());
        assertEquals(4, movements.get(0).getQuantity());
        assertSame(coffee, movements.get(0).getProduct());
        assertSame(so, movements.get(0).getSalesOrder());
        assertSame(refund, movements.get(0).getSalesRefund(), "the movement must point back at its refund");
        assertEquals(StockActivity.SI, movements.get(1).getActivity());
        assertEquals(DocType.SR, movements.get(1).getDocumentType());
        assertEquals(REFUND_NO, movements.get(1).getDocumentNumber());
        assertSame(refund, movements.get(1).getSalesRefund());
        assertEquals(2, movements.get(1).getQuantity());
        assertSame(tea, movements.get(1).getProduct());
    }

    @Test
    void shouldPutTheReturnedQuantityBackOnTheStockPosition() {
        Product coffee = product(7L, "Kopi Robusta 1kg", 6);
        Product tea = product(8L, "Teh Hijau 500g", 8);
        StockPosition coffeePosition = coffee.getStockPosition();
        givenCancelledAfterSubmit(coffee, 4, tea, 2);

        service.doCancel(cancel(90L));

        List<StockPosition> positions = savedPositions();
        assertEquals(2, positions.size());
        assertSame(coffeePosition, positions.get(0), "the existing row must be updated, not replaced");
        assertEquals(10, positions.get(0).getQuantity());
        assertEquals(10, positions.get(1).getQuantity());
    }

    @Test
    void shouldLockTheStockPositionsBeforeReadingWhatWasTaken() {
        Product coffee = product(7L, "Kopi Robusta 1kg", 6);
        Product tea = product(8L, "Teh Hijau 500g", 8);
        givenCancelledAfterSubmit(coffee, 4, tea, 2);

        service.doCancel(cancel(90L));

        // Reading the movements loads their products, and with them the positions;
        // read before the lock, those quantities could already be stale.
        InOrder inOrder = inOrder(soRepository, stockPositionRepository, stockRepository);
        inOrder.verify(soRepository).lockById(90L);
        inOrder.verify(stockPositionRepository).lockByProductIds(List.of(7L, 8L));
        inOrder.verify(stockRepository).findBySalesOrderIdAndActivityOrderByIdAsc(90L, StockActivity.SO);
    }

    @Test
    void shouldReturnTheStockOnlyOnceWhenTheCancelIsDeliveredAgain() {
        Product coffee = product(7L, "Kopi Robusta 1kg", 10);
        Product tea = product(8L, "Teh Hijau 500g", 10);
        givenCancelledAfterSubmit(coffee, 4, tea, 2);
        when(stockRepository.existsBySalesOrderIdAndActivity(90L, StockActivity.SI)).thenReturn(true);

        Response response = service.doCancel(cancel(90L));

        assertEquals(200, response.getCode());
        verify(stockRepository, never()).saveAll(anyList());
        verifyNoInteractions(stockPositionRepository);
        verify(refundRepository, never()).findBySalesOrderIdAndIdInOrderByIdAsc(anyLong(), anyCollection());
        assertEquals(10, coffee.getStockPosition().getQuantity());
    }

    @Test
    void shouldReturnNothingForAnOrderWhoseStockWasNeverTaken() {
        // The cancel overtook the submit, which then skips the cancelled order.
        Product coffee = product(7L, "Kopi Robusta 1kg", 10);
        SalesOrder so = given(storedOrder(detail(80L, coffee, 4)));
        so.setStatus(SalesStatus.CANCELLED);
        when(stockRepository.findBySalesOrderIdAndActivityOrderByIdAsc(90L, StockActivity.SO)).thenReturn(List.of());

        Response response = service.doCancel(cancel(90L));

        assertEquals(200, response.getCode());
        verify(stockRepository, never()).saveAll(anyList());
        verify(stockPositionRepository, never()).saveAll(any());
        assertEquals(10, coffee.getStockPosition().getQuantity());
    }

    @ParameterizedTest
    @EnumSource(value = SalesStatus.class, names = "CANCELLED", mode = EnumSource.Mode.EXCLUDE)
    void shouldRefuseToReturnTheStockOfAnOrderThatIsNotCancelled(SalesStatus status) {
        Product coffee = product(7L, "Kopi Robusta 1kg", 6);
        given(storedOrder(detail(80L, coffee, 4))).setStatus(status);

        BadRequestException thrown = assertThrows(BadRequestException.class, () -> service.doCancel(cancel(90L)));

        assertEquals("Sales order SO/2026/09/0001 is not cancelled", thrown.getMessage());
        verifyNoInteractions(stockRepository, stockPositionRepository);
    }

    @Test
    void shouldRecordOneMovementPerRefundSplitInProportionToItsAmount() {
        // Paid, and so refunded, in two instalments of 40% and 60%.
        Product coffee = product(7L, "Kopi Robusta 1kg", 6);
        Product tea = product(8L, "Teh Hijau 500g", 8);
        SalesOrder so = givenCancelledAfterSubmit(coffee, 4, tea, 1);

        Refund first = refund(501L, "RF20260923001", "40000.00");
        Refund second = refund(502L, "RF20260923002", "60000.00");

        service.doCancel(cancel(90L, first, second));

        // Coffee 4 is 1.6 + 2.4: the whole parts give 1 + 2 and the unit left over goes to
        // the larger fraction, 0.6. Tea 1 is 0.4 + 0.6, so all of it goes to the second
        // refund and the first records no tea at all.
        List<Stock> movements = savedStocks();
        assertEquals(3, movements.size());
        assertMovement(movements.get(0), first, coffee, 2);
        assertMovement(movements.get(1), second, coffee, 2);
        assertMovement(movements.get(2), second, tea, 1);
        movements.forEach(movement -> assertSame(so, movement.getSalesOrder()));

        List<StockPosition> positions = savedPositions();
        assertEquals(10, positions.get(0).getQuantity(), "the split must still return all the coffee");
        assertEquals(9, positions.get(1).getQuantity(), "the split must still return all the tea");
    }

    @Test
    void shouldGiveTheLeftoverUnitToTheEarlierRefundOnATie() {
        Product coffee = product(7L, "Kopi Robusta 1kg", 0);
        Product tea = product(8L, "Teh Hijau 500g", 0);
        givenCancelledAfterSubmit(coffee, 3, tea, 1);

        Refund first = refund(501L, "RF20260923001", "50000.00");
        Refund second = refund(502L, "RF20260923002", "50000.00");

        service.doCancel(cancel(90L, first, second));

        List<Stock> movements = savedStocks();
        assertEquals(3, movements.size());
        assertMovement(movements.get(0), first, coffee, 2);
        assertMovement(movements.get(1), first, tea, 1);
        assertMovement(movements.get(2), second, coffee, 1);
    }

    @Test
    void shouldSplitAcrossManyInstalmentsWithoutLosingAUnit() {
        Product coffee = product(7L, "Kopi Robusta 1kg", 0);
        Product tea = product(8L, "Teh Hijau 500g", 0);
        givenCancelledAfterSubmit(coffee, 10, tea, 7);

        service.doCancel(cancel(90L, refund(501L, "RF20260923001", "10000.00"),
                refund(502L, "RF20260923002", "33333.33"), refund(503L, "RF20260923003", "56666.67")));

        List<Stock> movements = savedStocks();
        assertEquals(10, movements.stream().filter(m -> m.getProduct() == coffee).mapToInt(Stock::getQuantity).sum());
        assertEquals(7, movements.stream().filter(m -> m.getProduct() == tea).mapToInt(Stock::getQuantity).sum());
        assertTrue(movements.stream().allMatch(m -> m.getQuantity() > 0), "a movement never records nothing");
        assertEquals(10, coffee.getStockPosition().getQuantity());
        assertEquals(7, tea.getStockPosition().getQuantity());
    }

    @Test
    void shouldReadTheNamedRefundsOfTheOrderOnce() {
        Product coffee = product(7L, "Kopi Robusta 1kg", 6);
        Product tea = product(8L, "Teh Hijau 500g", 8);
        givenCancelledAfterSubmit(coffee, 4, tea, 2);
        Refund refund = refund(501L, REFUND_NO, "9000");
        when(refundRepository.findBySalesOrderIdAndIdInOrderByIdAsc(eq(90L), anyCollection()))
                .thenReturn(List.of(refund));

        // A refund named twice is still one refund.
        service.doCancel(cancelWithIds(90L, List.of(501L, 501L)));

        verify(refundRepository).findBySalesOrderIdAndIdInOrderByIdAsc(90L, Set.of(501L));
        assertEquals(2, savedStocks().size());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldRefuseToReturnTheStockWithoutRefunds(List<Long> refundIds) {
        Product coffee = product(7L, "Kopi Robusta 1kg", 6);
        Product tea = product(8L, "Teh Hijau 500g", 8);
        givenCancelledAfterSubmit(coffee, 4, tea, 2);

        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> service.doCancel(cancelWithIds(90L, refundIds)));

        assertEquals("Sales order SO/2026/09/0001 cancel has no refunds", thrown.getMessage());
        verifyNoInteractions(stockRepository, stockPositionRepository, refundRepository);
    }

    @Test
    void shouldRefuseToReturnTheStockUnderARefundOfAnotherOrder() {
        Product coffee = product(7L, "Kopi Robusta 1kg", 6);
        Product tea = product(8L, "Teh Hijau 500g", 8);
        givenCancelledAfterSubmit(coffee, 4, tea, 2);
        // 502 is not a refund of this order, so only 501 comes back.
        when(refundRepository.findBySalesOrderIdAndIdInOrderByIdAsc(eq(90L), anyCollection()))
                .thenReturn(List.of(refund(501L, REFUND_NO, "40000.00")));

        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> service.doCancel(cancelWithIds(90L, List.of(501L, 502L))));

        assertEquals("Sales order SO/2026/09/0001 cancel names refunds that are not of this order",
                thrown.getMessage());
        verify(stockRepository, never()).saveAll(anyList());
        verify(stockPositionRepository, never()).saveAll(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1"})
    void shouldRefuseToReturnTheStockUnderARefundWithoutAPositiveAmount(String amount) {
        Product coffee = product(7L, "Kopi Robusta 1kg", 6);
        Product tea = product(8L, "Teh Hijau 500g", 8);
        givenCancelledAfterSubmit(coffee, 4, tea, 2);

        BadRequestException thrown = assertThrows(BadRequestException.class, () -> service.doCancel(
                cancel(90L, refund(501L, REFUND_NO, "60000.00"), refund(502L, "RF20260923002", amount))));

        assertEquals("Refund RF20260923002 has no positive amount", thrown.getMessage());
        verify(stockRepository, never()).saveAll(anyList());
        verify(stockPositionRepository, never()).saveAll(any());
    }

    @Test
    void shouldRefuseToCancelAnOrderThatDoesNotExist() {
        when(soRepository.lockById(anyLong())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.doCancel(cancel(90L)));

        verify(stockPositionRepository, never()).lockByProductIds(anyCollection());
        verifyNoInteractions(stockRepository);
    }
}
