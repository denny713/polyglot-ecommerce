package com.inventory.api.service;

import com.inventory.api.enums.DocStatus;
import com.inventory.api.enums.DocType;
import com.inventory.api.enums.StockActivity;
import com.inventory.api.exception.BadRequestException;
import com.inventory.api.exception.NotFoundException;
import com.inventory.api.exception.ServiceException;
import com.inventory.api.model.dto.request.pr.PRDetailSubmitReq;
import com.inventory.api.model.dto.request.pr.PRSearchReq;
import com.inventory.api.model.dto.request.pr.PRSubmitReq;
import com.inventory.api.model.dto.response.PagingResponse;
import com.inventory.api.model.dto.response.Response;
import com.inventory.api.model.dto.response.pr.PRRes;
import com.inventory.api.model.entity.Product;
import com.inventory.api.model.entity.PurchaseReturn;
import com.inventory.api.model.entity.PurchaseReturnDetail;
import com.inventory.api.model.entity.Stock;
import com.inventory.api.model.entity.StockPosition;
import com.inventory.api.model.entity.Supplier;
import com.inventory.api.repository.DocumentNumberRepository;
import com.inventory.api.repository.PurchaseReturnDetailRepository;
import com.inventory.api.repository.PurchaseReturnRepository;
import com.inventory.api.repository.StockPositionRepository;
import com.inventory.api.repository.StockRepository;
import com.inventory.api.repository.SupplierRepository;
import com.inventory.api.service.impl.PurchaseReturnServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit tests for the purchase return lifecycle. */
class PurchaseReturnServiceTest {

    private static final BigDecimal PRICE = new BigDecimal("1500");

    private DocumentNumberRepository docNoRepository;
    private PurchaseReturnRepository prRepository;
    private PurchaseReturnDetailRepository prDetailRepository;
    private SupplierRepository supplierRepository;
    private StockRepository stockRepository;
    private StockPositionRepository stockPositionRepository;

    private PurchaseReturnServiceImpl service;

    @BeforeEach
    void setUp() {
        docNoRepository = mock(DocumentNumberRepository.class);
        prRepository = mock(PurchaseReturnRepository.class);
        prDetailRepository = mock(PurchaseReturnDetailRepository.class);
        supplierRepository = mock(SupplierRepository.class);
        stockRepository = mock(StockRepository.class);
        stockPositionRepository = mock(StockPositionRepository.class);

        service = new PurchaseReturnServiceImpl(docNoRepository, prRepository, prDetailRepository,
                supplierRepository, stockRepository, stockPositionRepository);
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    private static Product product(Long id, String name, Integer onHand) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setBuyPrice(PRICE);

        if (onHand != null) {
            StockPosition position = new StockPosition();
            position.setId(300L);
            position.setQuantity(onHand);
            // The back-reference is left unset on purpose -- see the note in
            // PurchaseOrderServiceImplTest: the generated equals/hashCode of these
            // entities recurse through a Product <-> StockPosition cycle.
            product.setStockPosition(position);
        }

        return product;
    }

    private static Supplier supplier(Product... products) {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setName("PT Sumber Makmur");
        supplier.setProducts(new ArrayList<>(List.of(products)));

        return supplier;
    }

    private static PRSubmitReq request(PRDetailSubmitReq... details) {
        PRSubmitReq req = new PRSubmitReq();
        req.setSupplierId(1L);
        req.setDetails(new ArrayList<>(List.of(details)));

        return req;
    }

    private static PRDetailSubmitReq detailReq(Long id, Long productId, int quantity) {
        PRDetailSubmitReq detail = new PRDetailSubmitReq();
        detail.setId(id);
        detail.setProductId(productId);
        detail.setQuantity(quantity);

        return detail;
    }

    private static PurchaseReturnDetail storedDetail(Long id, Product product, int quantity) {
        PurchaseReturnDetail detail = new PurchaseReturnDetail();
        detail.setId(id);
        detail.setProduct(product);
        detail.setQuantity(quantity);
        detail.setUnitPrice(PRICE);
        detail.setSubtotal(PRICE.multiply(BigDecimal.valueOf(quantity)));

        return detail;
    }

    private static PurchaseReturn storedReturn(DocStatus status, Supplier supplier, PurchaseReturnDetail... details) {
        PurchaseReturn pr = new PurchaseReturn();
        pr.setId(60L);
        pr.setDocumentNumber("PR/2026/09/0001");
        pr.setStatus(status);
        pr.setSupplier(supplier);
        pr.setPurchaseReturnDetails(new ArrayList<>(List.of(details)));

        return pr;
    }

    private void echoDetailSaves() {
        when(prDetailRepository.saveAll(any())).thenAnswer(call -> {
            List<PurchaseReturnDetail> saved = call.getArgument(0);
            return new ArrayList<>(saved);
        });
    }

    // ------------------------------------------------------------------
    // doSubmit -- create
    // ------------------------------------------------------------------

    @Test
    void shouldCreateADraftWithAGeneratedDocumentNumber() {
        when(supplierRepository.doGet(1L)).thenReturn(supplier(product(7L, "Kopi Robusta 1kg", 20)));
        when(docNoRepository.generateDocumentNumber(anyString(), any(LocalDate.class)))
                .thenReturn("PR/2026/09/0007");
        echoDetailSaves();

        Response response = service.doSubmit(null, request(detailReq(null, 7L, 4)));

        assertEquals(201, response.getCode());

        ArgumentCaptor<PurchaseReturn> saved = ArgumentCaptor.forClass(PurchaseReturn.class);
        verify(prRepository).save(saved.capture());

        PurchaseReturn pr = saved.getValue();
        assertEquals(DocStatus.DRAFT, pr.getStatus());
        assertEquals("PR/2026/09/0007", pr.getDocumentNumber());
        assertEquals(new BigDecimal("6000"), pr.getGrandTotal());

        // The prefix must be PR, not PO.
        verify(docNoRepository).generateDocumentNumber(DocType.PR.name(),
                LocalDate.now(ZoneId.of("Asia/Jakarta")));
    }

    @Test
    void shouldDefaultABlankReasonAndNoteToADash() {
        when(supplierRepository.doGet(1L)).thenReturn(supplier(product(7L, "Kopi Robusta 1kg", 20)));
        when(docNoRepository.generateDocumentNumber(anyString(), any(LocalDate.class))).thenReturn("PR/1");
        echoDetailSaves();

        service.doSubmit(null, request(detailReq(null, 7L, 1)));

        ArgumentCaptor<PurchaseReturn> saved = ArgumentCaptor.forClass(PurchaseReturn.class);
        verify(prRepository).save(saved.capture());
        assertEquals("-", saved.getValue().getReason());
        assertEquals("-", saved.getValue().getNote());

        // The per-line reason and note fall back the same way.
        PurchaseReturnDetail line = lastDetailSave().get(0);
        assertEquals("-", line.getReason());
        assertEquals("-", line.getNote());
    }

    @Test
    void shouldKeepTheDocumentAndLineReasonsSeparate() {
        when(supplierRepository.doGet(1L)).thenReturn(supplier(product(7L, "Kopi Robusta 1kg", 20)));
        when(docNoRepository.generateDocumentNumber(anyString(), any(LocalDate.class))).thenReturn("PR/1");
        echoDetailSaves();

        PRDetailSubmitReq detail = detailReq(null, 7L, 1);
        detail.setReason("damaged on arrival");

        PRSubmitReq req = request(detail);
        req.setReason("wrong shipment");

        service.doSubmit(null, req);

        ArgumentCaptor<PurchaseReturn> saved = ArgumentCaptor.forClass(PurchaseReturn.class);
        verify(prRepository).save(saved.capture());

        assertEquals("wrong shipment", saved.getValue().getReason());
        assertEquals("damaged on arrival", lastDetailSave().get(0).getReason());
    }

    @Test
    void shouldCopyThePriceOntoTheLineAtSubmitTime() {
        when(supplierRepository.doGet(1L)).thenReturn(supplier(product(7L, "Kopi Robusta 1kg", 20)));
        when(docNoRepository.generateDocumentNumber(anyString(), any(LocalDate.class))).thenReturn("PR/1");
        echoDetailSaves();

        service.doSubmit(null, request(detailReq(null, 7L, 3)));

        PurchaseReturnDetail line = lastDetailSave().get(0);
        assertEquals(PRICE, line.getUnitPrice(), "the credit is computed at the purchase price");
        assertEquals(new BigDecimal("4500"), line.getSubtotal());
    }

    @Test
    void shouldRejectAProductTheSupplierDoesNotOffer() {
        when(supplierRepository.doGet(1L)).thenReturn(supplier(product(7L, "Kopi Robusta 1kg", 20)));
        when(docNoRepository.generateDocumentNumber(anyString(), any(LocalDate.class))).thenReturn("PR/1");

        NotFoundException thrown = assertThrows(NotFoundException.class,
                () -> service.doSubmit(null, request(detailReq(null, 99L, 1))));

        assertTrue(thrown.getMessage().contains("99"));
        verify(prRepository, never()).save(any());
    }

    @Test
    void shouldRejectASubmitWithNoDetails() {
        when(supplierRepository.doGet(1L)).thenReturn(supplier(product(7L, "Kopi Robusta 1kg", 20)));
        when(docNoRepository.generateDocumentNumber(anyString(), any(LocalDate.class))).thenReturn("PR/1");

        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> service.doSubmit(null, request()));

        assertEquals("Details cannot be empty", thrown.getMessage());
    }

    // ------------------------------------------------------------------
    // doSubmit -- update
    // ------------------------------------------------------------------

    @Test
    void shouldUpdateAnExistingDraft() {
        Product product = product(7L, "Kopi Robusta 1kg", 20);
        Supplier supplier = supplier(product);
        when(prRepository.doGet(60L))
                .thenReturn(storedReturn(DocStatus.DRAFT, supplier, storedDetail(90L, product, 4)));
        when(supplierRepository.doGet(1L)).thenReturn(supplier);
        echoDetailSaves();

        Response response = service.doSubmit(60L, request(detailReq(90L, 7L, 6)));

        assertEquals(200, response.getCode());
        verifyNoInteractions(docNoRepository);
    }

    @Test
    void shouldSoftDeleteLinesTheRequestNoLongerMentions() {
        Product kept = product(7L, "Kopi Robusta 1kg", 20);
        Product dropped = product(8L, "Gula Pasir 1kg", 20);
        Supplier supplier = supplier(kept, dropped);

        PurchaseReturnDetail droppedLine = storedDetail(91L, dropped, 2);
        when(prRepository.doGet(60L)).thenReturn(
                storedReturn(DocStatus.DRAFT, supplier, storedDetail(90L, kept, 4), droppedLine));
        when(supplierRepository.doGet(1L)).thenReturn(supplier);
        echoDetailSaves();

        service.doSubmit(60L, request(detailReq(90L, 7L, 4)));

        assertTrue(droppedLine.getIsDeleted());
    }

    @Test
    void shouldRejectTheSameLineIdTwiceInOneRequest() {
        Product product = product(7L, "Kopi Robusta 1kg", 20);
        Supplier supplier = supplier(product);
        when(prRepository.doGet(60L))
                .thenReturn(storedReturn(DocStatus.DRAFT, supplier, storedDetail(90L, product, 4)));
        when(supplierRepository.doGet(1L)).thenReturn(supplier);

        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> service.doSubmit(60L, request(detailReq(90L, 7L, 1), detailReq(90L, 7L, 2))));

        assertEquals("Duplicate detail id 90 in the request", thrown.getMessage());
    }

    @Test
    void shouldRejectALineIdFromAnotherDocument() {
        Product product = product(7L, "Kopi Robusta 1kg", 20);
        Supplier supplier = supplier(product);
        when(prRepository.doGet(60L))
                .thenReturn(storedReturn(DocStatus.DRAFT, supplier, storedDetail(90L, product, 4)));
        when(supplierRepository.doGet(1L)).thenReturn(supplier);

        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> service.doSubmit(60L, request(detailReq(999L, 7L, 1))));

        assertTrue(thrown.getMessage().contains("999"));
    }

    @Test
    void shouldRefuseToSubmitAnApprovedDocument() {
        Product product = product(7L, "Kopi Robusta 1kg", 20);
        Supplier supplier = supplier(product);
        when(prRepository.doGet(60L))
                .thenReturn(storedReturn(DocStatus.APPROVED, supplier, storedDetail(90L, product, 4)));
        when(supplierRepository.doGet(1L)).thenReturn(supplier);

        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> service.doSubmit(60L, request(detailReq(90L, 7L, 4))));

        assertEquals("Only draft or cancelled purchase return can be submitted", thrown.getMessage());
    }

    @Test
    void shouldReopenACancelledDocumentAsDraft() {
        Product product = product(7L, "Kopi Robusta 1kg", 20);
        Supplier supplier = supplier(product);
        PurchaseReturn stored = storedReturn(DocStatus.CANCELLED, supplier, storedDetail(90L, product, 4));

        when(prRepository.doGet(60L)).thenReturn(stored);
        when(supplierRepository.doGet(1L)).thenReturn(supplier);
        echoDetailSaves();

        service.doSubmit(60L, request(detailReq(90L, 7L, 4)));

        assertEquals(DocStatus.DRAFT, stored.getStatus());
    }

    // ------------------------------------------------------------------
    // doApprove -- stock goes out, and the quantities come from the document
    // ------------------------------------------------------------------

    @Test
    void shouldApproveAndRecordAStockOutMovement() {
        Product product = product(7L, "Kopi Robusta 1kg", 20);
        PurchaseReturn stored = storedReturn(DocStatus.DRAFT, supplier(product), storedDetail(90L, product, 4));
        when(prRepository.doGet(60L)).thenReturn(stored);

        Response response = service.doApprove(60L);

        assertEquals(200, response.getCode());
        assertEquals(DocStatus.APPROVED, stored.getStatus());
        assertEquals(new BigDecimal("6000"), stored.getGrandTotal());

        Stock movement = savedStocks().get(0);
        assertEquals(StockActivity.SO, movement.getActivity(), "sending goods back is a stock out");
        assertEquals(DocType.PR, movement.getDocumentType());
        assertEquals("PR/2026/09/0001", movement.getDocumentNumber());
        assertEquals(4, movement.getQuantity());
        assertSame(stored, movement.getPurchaseReturn());
    }

    @Test
    void shouldDeductFromTheStockPosition() {
        Product product = product(7L, "Kopi Robusta 1kg", 20);
        StockPosition position = product.getStockPosition();
        when(prRepository.doGet(60L))
                .thenReturn(storedReturn(DocStatus.DRAFT, supplier(product), storedDetail(90L, product, 4)));

        service.doApprove(60L);

        assertEquals(16, position.getQuantity(), "20 on hand minus 4 returned");
        assertSame(position, savedPositions().get(0));
    }

    @Test
    void shouldDeductOnceForAProductRepeatedAcrossLines() {
        Product product = product(7L, "Kopi Robusta 1kg", 20);
        when(prRepository.doGet(60L)).thenReturn(storedReturn(DocStatus.DRAFT, supplier(product),
                storedDetail(90L, product, 4), storedDetail(91L, product, 6)));

        service.doApprove(60L);

        List<StockPosition> positions = savedPositions();
        assertEquals(1, positions.size());
        assertEquals(10, positions.get(0).getQuantity(), "20 on hand minus 4 and 6");
    }

    @Test
    void shouldRefuseToReturnMoreThanIsOnHand() {
        Product product = product(7L, "Kopi Robusta 1kg", 3);
        when(prRepository.doGet(60L))
                .thenReturn(storedReturn(DocStatus.DRAFT, supplier(product), storedDetail(90L, product, 4)));

        BadRequestException thrown = assertThrows(BadRequestException.class, () -> service.doApprove(60L));

        assertTrue(thrown.getMessage().contains("(4)"));
        assertTrue(thrown.getMessage().contains("(3)"));
        assertTrue(thrown.getMessage().contains("Kopi Robusta 1kg"));

        // Nothing may be written once the check fails.
        verifyNoInteractions(stockRepository);
        verifyNoInteractions(stockPositionRepository);
    }

    @Test
    void shouldRefuseToReturnAProductThatNeverMoved() {
        // No stock position at all means nothing on hand, so any return fails.
        Product product = product(7L, "Kopi Robusta 1kg", null);
        when(prRepository.doGet(60L))
                .thenReturn(storedReturn(DocStatus.DRAFT, supplier(product), storedDetail(90L, product, 1)));

        BadRequestException thrown = assertThrows(BadRequestException.class, () -> service.doApprove(60L));

        assertTrue(thrown.getMessage().contains("(0)"));
    }

    @Test
    void shouldAllowReturningEverythingOnHand() {
        Product product = product(7L, "Kopi Robusta 1kg", 4);
        StockPosition position = product.getStockPosition();
        when(prRepository.doGet(60L))
                .thenReturn(storedReturn(DocStatus.DRAFT, supplier(product), storedDetail(90L, product, 4)));

        service.doApprove(60L);

        assertEquals(0, position.getQuantity(), "emptying the shelf is allowed, going below it is not");
    }

    @Test
    void shouldRefuseToApproveAnythingButADraft() {
        Product product = product(7L, "Kopi Robusta 1kg", 20);
        when(prRepository.doGet(60L))
                .thenReturn(storedReturn(DocStatus.APPROVED, supplier(product), storedDetail(90L, product, 4)));

        BadRequestException thrown = assertThrows(BadRequestException.class, () -> service.doApprove(60L));

        assertEquals("Only draft purchase return can be approved", thrown.getMessage());
        verifyNoInteractions(stockRepository);
    }

    // ------------------------------------------------------------------
    // doCancel
    // ------------------------------------------------------------------

    @Test
    void shouldCancelADraft() {
        Product product = product(7L, "Kopi Robusta 1kg", 20);
        PurchaseReturn stored = storedReturn(DocStatus.DRAFT, supplier(product), storedDetail(90L, product, 4));
        when(prRepository.doGet(60L)).thenReturn(stored);
        when(prRepository.save(stored)).thenReturn(stored);

        assertEquals(200, service.doCancel(60L).getCode());
        assertEquals(DocStatus.CANCELLED, stored.getStatus());
    }

    @Test
    void shouldRefuseToCancelAnythingButADraft() {
        Product product = product(7L, "Kopi Robusta 1kg", 20);
        when(prRepository.doGet(60L))
                .thenReturn(storedReturn(DocStatus.CANCELLED, supplier(product), storedDetail(90L, product, 4)));

        BadRequestException thrown = assertThrows(BadRequestException.class, () -> service.doCancel(60L));

        assertEquals("Only draft purchase return can be canceled", thrown.getMessage());
        verify(prRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // read and visibility
    // ------------------------------------------------------------------

    @Test
    void shouldReadOneDocument() {
        Product product = product(7L, "Kopi Robusta 1kg", 20);
        when(prRepository.doGet(60L))
                .thenReturn(storedReturn(DocStatus.DRAFT, supplier(product), storedDetail(90L, product, 4)));

        PRRes body = (PRRes) service.doDetail(60L).getData();

        assertEquals("PR/2026/09/0001", body.getDocumentNumber());
        assertEquals(1L, body.getSupplierId());
        assertEquals("PT Sumber Makmur", body.getSupplierName());
        assertEquals("Kopi Robusta 1kg", body.getDetails().get(0).getProductName());
    }

    @Test
    void shouldActivate() {
        Product product = product(7L, "Kopi Robusta 1kg", 20);
        when(prRepository.doActivate(60L))
                .thenReturn(storedReturn(DocStatus.DRAFT, supplier(product), storedDetail(90L, product, 4)));

        assertEquals(200, service.doActivate(60L).getCode());
        verify(prRepository).doActivate(60L);
    }

    @Test
    void shouldDeactivate() {
        Product product = product(7L, "Kopi Robusta 1kg", 20);
        when(prRepository.doDeactivate(60L))
                .thenReturn(storedReturn(DocStatus.DRAFT, supplier(product), storedDetail(90L, product, 4)));

        assertEquals(200, service.doDeactivate(60L).getCode());
        verify(prRepository).doDeactivate(60L);
    }

    @Test
    void shouldDelete() {
        Product product = product(7L, "Kopi Robusta 1kg", 20);
        when(prRepository.doDelete(60L))
                .thenReturn(storedReturn(DocStatus.DRAFT, supplier(product), storedDetail(90L, product, 4)));

        assertEquals(200, service.doDelete(60L).getCode());
        verify(prRepository).doDelete(60L);
    }

    // ------------------------------------------------------------------
    // doSearch
    // ------------------------------------------------------------------

    @Test
    void shouldSearchAndMapEveryRow() {
        Product product = product(7L, "Kopi Robusta 1kg", 20);
        when(prRepository.doSearch(any(), any(PRSearchReq.class))).thenReturn(new PageImpl<>(List.of(
                storedReturn(DocStatus.DRAFT, supplier(product), storedDetail(90L, product, 4)))));

        PagingResponse response = service.doSearch(new PRSearchReq());

        assertEquals(200, response.getCode());
        assertEquals(1L, response.getTotalRecord());
        assertEquals(1, ((List<?>) response.getData()).size());
    }

    @Test
    void shouldWrapAFailedSearchAsAServiceException() {
        when(prRepository.doSearch(any(), any(PRSearchReq.class)))
                .thenThrow(new IllegalStateException("could not extract ResultSet"));

        ServiceException thrown = assertThrows(ServiceException.class, () -> service.doSearch(new PRSearchReq()));

        assertEquals("could not extract ResultSet", thrown.getMessage());
    }

    @Test
    void shouldSearchWithASpecificationBuiltFromTheRequest() {
        when(prRepository.doSearch(any(), any(PRSearchReq.class))).thenReturn(new PageImpl<>(List.of()));

        PRSearchReq req = new PRSearchReq();
        req.setDocumentNumber("PR/2026");

        service.doSearch(req);

        ArgumentCaptor<Specification<PurchaseReturn>> spec = ArgumentCaptor.forClass(Specification.class);
        verify(prRepository).doSearch(spec.capture(), any(PRSearchReq.class));
        assertNotNull(spec.getValue());
    }

    // ------------------------------------------------------------------
    // captors
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<PurchaseReturnDetail> lastDetailSave() {
        ArgumentCaptor<Iterable<PurchaseReturnDetail>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(prDetailRepository, atLeastOnce()).saveAll(captor.capture());

        return toList(captor.getAllValues().get(captor.getAllValues().size() - 1));
    }

    @SuppressWarnings("unchecked")
    private List<Stock> savedStocks() {
        ArgumentCaptor<Iterable<Stock>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(stockRepository).saveAll(captor.capture());

        return toList(captor.getValue());
    }

    @SuppressWarnings("unchecked")
    private List<StockPosition> savedPositions() {
        ArgumentCaptor<Iterable<StockPosition>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(stockPositionRepository).saveAll(captor.capture());

        return toList(captor.getValue());
    }

    private static <T> List<T> toList(Iterable<T> values) {
        List<T> result = new ArrayList<>();
        values.forEach(result::add);

        return result;
    }
}
