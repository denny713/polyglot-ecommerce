package com.inventory.api.service;

import com.inventory.api.enums.DocType;
import com.inventory.api.enums.SalesStatus;
import com.inventory.api.enums.StockActivity;
import com.inventory.api.exception.BadRequestException;
import com.inventory.api.model.dto.request.so.SOSubmitReq;
import com.inventory.api.model.dto.response.Response;
import com.inventory.api.model.dto.response.so.SODetailRes;
import com.inventory.api.model.dto.response.so.SORes;
import com.inventory.api.model.entity.Product;
import com.inventory.api.model.entity.SalesOrder;
import com.inventory.api.model.entity.SalesOrderDetail;
import com.inventory.api.model.entity.Stock;
import com.inventory.api.model.entity.StockPosition;
import com.inventory.api.repository.SalesOrderDetailRepository;
import com.inventory.api.repository.SalesOrderRepository;
import com.inventory.api.repository.StockPositionRepository;
import com.inventory.api.repository.StockRepository;
import com.inventory.api.service.impl.SalesOrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit tests for submitting a sales order. */
class SalesOrderServiceTest {

    private static final BigDecimal PRICE = new BigDecimal("1500");

    private SalesOrderRepository soRepository;
    private SalesOrderDetailRepository soDetailRepository;
    private StockRepository stockRepository;
    private StockPositionRepository stockPositionRepository;

    private SalesOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        soRepository = mock(SalesOrderRepository.class);
        soDetailRepository = mock(SalesOrderDetailRepository.class);
        stockRepository = mock(StockRepository.class);
        stockPositionRepository = mock(StockPositionRepository.class);

        service = new SalesOrderServiceImpl(soRepository, soDetailRepository,
                stockRepository, stockPositionRepository);
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
        when(soRepository.doGet(90L)).thenReturn(stored);

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
        when(soRepository.doGet(90L)).thenReturn(storedOrder(detail(80L, coffee, 4)));

        service.doSubmit(request(90L));

        List<StockPosition> positions = savedPositions();

        assertEquals(1, positions.size());
        assertSame(existing, positions.get(0), "the existing row must be updated, not replaced");
        assertEquals(6, positions.get(0).getQuantity());
    }

    @Test
    void shouldDeductOnceForAProductRepeatedAcrossLines() {
        Product coffee = product(7L, "Kopi Robusta 1kg", 10);
        when(soRepository.doGet(90L))
                .thenReturn(storedOrder(detail(80L, coffee, 4), detail(81L, coffee, 6)));

        service.doSubmit(request(90L));

        List<StockPosition> positions = savedPositions();

        // One row holding the remainder, not two rows racing each other.
        assertEquals(1, positions.size());
        assertEquals(0, positions.get(0).getQuantity());
    }

    @Test
    void shouldAllowSellingTheLastOfTheStock() {
        Product coffee = product(7L, "Kopi Robusta 1kg", 4);
        when(soRepository.doGet(90L)).thenReturn(storedOrder(detail(80L, coffee, 4)));

        service.doSubmit(request(90L));

        assertEquals(0, savedPositions().get(0).getQuantity(), "an exact match is not an overdraw");
    }

    @Test
    void shouldRefuseToSellMoreThanIsHeld() {
        Product coffee = product(7L, "Kopi Robusta 1kg", 3);
        when(soRepository.doGet(90L)).thenReturn(storedOrder(detail(80L, coffee, 4)));

        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> service.doSubmit(request(90L)));

        assertTrue(thrown.getMessage().contains("(4)"));
        assertTrue(thrown.getMessage().contains("(3)"));
        assertTrue(thrown.getMessage().contains("Kopi Robusta 1kg"));

        // Nothing may be written when one line of the order cannot be served.
        verifyNoInteractions(stockRepository);
        verifyNoInteractions(stockPositionRepository);
    }

    @Test
    void shouldRefuseAProductThatHasNeverBeenStocked() {
        Product coffee = product(7L, "Kopi Robusta 1kg", null);
        when(soRepository.doGet(90L)).thenReturn(storedOrder(detail(80L, coffee, 1)));

        // A product with no position row counts as zero on hand rather than as
        // unlimited, so the order is refused instead of driving stock negative.
        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> service.doSubmit(request(90L)));

        assertTrue(thrown.getMessage().contains("(0)"));
        verifyNoInteractions(stockRepository);
    }

    @Test
    void shouldCountTheSecondLineAgainstWhatTheFirstOneLeft() {
        Product coffee = product(7L, "Kopi Robusta 1kg", 5);
        when(soRepository.doGet(90L))
                .thenReturn(storedOrder(detail(80L, coffee, 4), detail(81L, coffee, 2)));

        // Each line alone fits; together they do not, which is only visible
        // because the running remainder is carried between lines.
        assertThrows(BadRequestException.class, () -> service.doSubmit(request(90L)));

        verifyNoInteractions(stockRepository);
    }

    // ------------------------------------------------------------------
    // doSubmit -- response
    // ------------------------------------------------------------------

    @Test
    void shouldReturnTheOrderWithItsLines() {
        Product coffee = product(7L, "Kopi Robusta 1kg", 10);
        when(soRepository.doGet(90L)).thenReturn(storedOrder(detail(80L, coffee, 4)));

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
        when(soRepository.doGet(90L)).thenReturn(storedOrder(detail(80L, coffee, 1)));

        service.doSubmit(request(90L));

        verify(soRepository).doGet(90L);
        // Submitting reads and moves stock; it does not rewrite the order or its
        // lines, which the order service owns.
        verifyNoInteractions(soDetailRepository);
    }
}
