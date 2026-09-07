package com.inventory.api.service.impl;

import com.inventory.api.enums.DocStatus;
import com.inventory.api.enums.DocType;
import com.inventory.api.enums.StockActivity;
import com.inventory.api.exception.BadRequestException;
import com.inventory.api.exception.NotFoundException;
import com.inventory.api.exception.ServiceException;
import com.inventory.api.model.dto.request.po.PODetailSubmitReq;
import com.inventory.api.model.dto.request.po.POSearchReq;
import com.inventory.api.model.dto.request.po.POSubmitReq;
import com.inventory.api.model.dto.response.PagingResponse;
import com.inventory.api.model.dto.response.Response;
import com.inventory.api.model.dto.response.po.PORes;
import com.inventory.api.model.entity.Product;
import com.inventory.api.model.entity.PurchaseOrder;
import com.inventory.api.model.entity.PurchaseOrderDetail;
import com.inventory.api.model.entity.Stock;
import com.inventory.api.model.entity.StockPosition;
import com.inventory.api.model.entity.Supplier;
import com.inventory.api.repository.DocumentNumberRepository;
import com.inventory.api.repository.PurchaseOrderDetailRepository;
import com.inventory.api.repository.PurchaseOrderRepository;
import com.inventory.api.repository.StockPositionRepository;
import com.inventory.api.repository.StockRepository;
import com.inventory.api.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the purchase order lifecycle.
 * <p>
 * Everything is mocked at the repository boundary, so these tests exercise the
 * decisions the service makes rather than the persistence around them. That is
 * where the rules live: which transitions are allowed, how a submitted details
 * list is reconciled against what is stored, and what happens to stock when a
 * document is approved.
 */
class PurchaseOrderServiceImplTest {

    private static final BigDecimal PRICE = new BigDecimal("1500");

    private DocumentNumberRepository docNoRepository;
    private PurchaseOrderRepository poRepository;
    private PurchaseOrderDetailRepository poDetailRepository;
    private SupplierRepository supplierRepository;
    private StockRepository stockRepository;
    private StockPositionRepository stockPositionRepository;

    private PurchaseOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        docNoRepository = mock(DocumentNumberRepository.class);
        poRepository = mock(PurchaseOrderRepository.class);
        poDetailRepository = mock(PurchaseOrderDetailRepository.class);
        supplierRepository = mock(SupplierRepository.class);
        stockRepository = mock(StockRepository.class);
        stockPositionRepository = mock(StockPositionRepository.class);

        service = new PurchaseOrderServiceImpl(docNoRepository, poRepository, poDetailRepository,
                supplierRepository, stockRepository, stockPositionRepository);
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    private static Product product(Long id, String name) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(PRICE);

        return product;
    }

    private static Supplier supplier(Product... products) {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setName("PT Sumber Makmur");
        supplier.setProducts(new ArrayList<>(List.of(products)));

        return supplier;
    }

    private static POSubmitReq request(PODetailSubmitReq... details) {
        POSubmitReq req = new POSubmitReq();
        req.setSupplierId(1L);
        req.setDetails(new ArrayList<>(List.of(details)));

        return req;
    }

    private static PODetailSubmitReq detailReq(Long id, Long productId, int quantity) {
        PODetailSubmitReq detail = new PODetailSubmitReq();
        detail.setId(id);
        detail.setProductId(productId);
        detail.setQuantity(quantity);

        return detail;
    }

    private static PurchaseOrderDetail storedDetail(Long id, Product product, int orderQuantity) {
        PurchaseOrderDetail detail = new PurchaseOrderDetail();
        detail.setId(id);
        detail.setProduct(product);
        detail.setOrderQuantity(orderQuantity);
        detail.setUnitPrice(PRICE);
        detail.setOrderSubtotal(PRICE.multiply(BigDecimal.valueOf(orderQuantity)));
        detail.setRealQuantity(0);
        detail.setRealSubtotal(BigDecimal.ZERO);

        return detail;
    }

    private static PurchaseOrder storedOrder(DocStatus status, Supplier supplier, PurchaseOrderDetail... details) {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(50L);
        po.setDocumentNumber("PO/2026/09/0001");
        po.setStatus(status);
        po.setSupplier(supplier);
        po.setPurchaseOrderDetails(new ArrayList<>(List.of(details)));

        return po;
    }

    /** Makes {@code saveAll} behave like a real one: return what it was given. */
    private void echoDetailSaves() {
        when(poDetailRepository.saveAll(any())).thenAnswer(call -> {
            List<PurchaseOrderDetail> saved = call.getArgument(0);
            return new ArrayList<>(saved);
        });
    }

    // ------------------------------------------------------------------
    // doSubmit -- create
    // ------------------------------------------------------------------

    @Test
    void shouldCreateADraftWithAGeneratedDocumentNumber() {
        Product product = product(7L, "Kopi Robusta 1kg");
        when(supplierRepository.doGet(1L)).thenReturn(supplier(product));
        when(docNoRepository.generateDocumentNumber(anyString(), any(LocalDate.class)))
                .thenReturn("PO/2026/09/0007");
        echoDetailSaves();

        Response response = service.doSubmit(null, request(detailReq(null, 7L, 4)));

        assertEquals(201, response.getCode(), "a create must report 201, not 200");

        ArgumentCaptor<PurchaseOrder> saved = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(poRepository).save(saved.capture());

        PurchaseOrder po = saved.getValue();
        assertEquals(DocStatus.DRAFT, po.getStatus());
        assertEquals("PO/2026/09/0007", po.getDocumentNumber());
        // 4 x 1500 ordered, nothing received yet.
        assertEquals(new BigDecimal("6000"), po.getOrderGrandTotal());
        assertEquals(BigDecimal.ZERO, po.getRealGrandTotal());

        // The document number must be asked for with the PO prefix.
        verify(docNoRepository).generateDocumentNumber(DocType.PO.name(),
                LocalDate.now(java.time.ZoneId.of("Asia/Jakarta")));
    }

    @Test
    void shouldDefaultABlankNoteToADash() {
        Product product = product(7L, "Kopi Robusta 1kg");
        when(supplierRepository.doGet(1L)).thenReturn(supplier(product));
        when(docNoRepository.generateDocumentNumber(anyString(), any(LocalDate.class))).thenReturn("PO/1");
        echoDetailSaves();

        service.doSubmit(null, request(detailReq(null, 7L, 1)));

        ArgumentCaptor<PurchaseOrder> saved = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(poRepository).save(saved.capture());
        assertEquals("-", saved.getValue().getNote(), "the column must never hold a blank");
    }

    @Test
    void shouldKeepAGivenNote() {
        Product product = product(7L, "Kopi Robusta 1kg");
        when(supplierRepository.doGet(1L)).thenReturn(supplier(product));
        when(docNoRepository.generateDocumentNumber(anyString(), any(LocalDate.class))).thenReturn("PO/1");
        echoDetailSaves();

        POSubmitReq req = request(detailReq(null, 7L, 1));
        req.setNote("urgent restock");

        service.doSubmit(null, req);

        ArgumentCaptor<PurchaseOrder> saved = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(poRepository).save(saved.capture());
        assertEquals("urgent restock", saved.getValue().getNote());
    }

    @Test
    void shouldCopyThePriceOntoTheLineAtSubmitTime() {
        Product product = product(7L, "Kopi Robusta 1kg");
        when(supplierRepository.doGet(1L)).thenReturn(supplier(product));
        when(docNoRepository.generateDocumentNumber(anyString(), any(LocalDate.class))).thenReturn("PO/1");
        echoDetailSaves();

        service.doSubmit(null, request(detailReq(null, 7L, 3)));

        PurchaseOrderDetail line = lastDetailSave().get(0);

        assertEquals(PRICE, line.getUnitPrice(), "the agreed price must be frozen on the line");
        assertEquals(new BigDecimal("4500"), line.getOrderSubtotal());
        assertEquals(0, line.getRealQuantity());
        assertEquals(BigDecimal.ZERO, line.getRealSubtotal());
    }

    @Test
    void shouldRejectAProductTheSupplierDoesNotOffer() {
        when(supplierRepository.doGet(1L)).thenReturn(supplier(product(7L, "Kopi Robusta 1kg")));
        when(docNoRepository.generateDocumentNumber(anyString(), any(LocalDate.class))).thenReturn("PO/1");

        NotFoundException thrown = assertThrows(NotFoundException.class,
                () -> service.doSubmit(null, request(detailReq(null, 99L, 1))));

        assertTrue(thrown.getMessage().contains("99"), "the message must name the offending product");
        assertTrue(thrown.getMessage().contains("PT Sumber Makmur"));
        verify(poRepository, never()).save(any());
    }

    @Test
    void shouldRejectASubmitWithNoDetails() {
        when(supplierRepository.doGet(1L)).thenReturn(supplier(product(7L, "Kopi Robusta 1kg")));
        when(docNoRepository.generateDocumentNumber(anyString(), any(LocalDate.class))).thenReturn("PO/1");

        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> service.doSubmit(null, request()));

        assertEquals("Details cannot be empty", thrown.getMessage());
    }

    // ------------------------------------------------------------------
    // doSubmit -- update, where the details list is reconciled
    // ------------------------------------------------------------------

    @Test
    void shouldUpdateAnExistingDraft() {
        Product product = product(7L, "Kopi Robusta 1kg");
        Supplier supplier = supplier(product);
        when(poRepository.doGet(50L)).thenReturn(storedOrder(DocStatus.DRAFT, supplier, storedDetail(80L, product, 4)));
        when(supplierRepository.doGet(1L)).thenReturn(supplier);
        echoDetailSaves();

        Response response = service.doSubmit(50L, request(detailReq(80L, 7L, 6)));

        assertEquals(200, response.getCode(), "an update must report 200, not 201");
        // An update must not burn a new document number.
        verifyNoInteractions(docNoRepository);
    }

    @Test
    void shouldSoftDeleteLinesTheRequestNoLongerMentions() {
        Product kept = product(7L, "Kopi Robusta 1kg");
        Product dropped = product(8L, "Gula Pasir 1kg");
        Supplier supplier = supplier(kept, dropped);

        PurchaseOrderDetail keptLine = storedDetail(80L, kept, 4);
        PurchaseOrderDetail droppedLine = storedDetail(81L, dropped, 2);

        when(poRepository.doGet(50L)).thenReturn(storedOrder(DocStatus.DRAFT, supplier, keptLine, droppedLine));
        when(supplierRepository.doGet(1L)).thenReturn(supplier);
        echoDetailSaves();

        service.doSubmit(50L, request(detailReq(80L, 7L, 4)));

        assertTrue(droppedLine.getIsDeleted(), "the omitted line must be soft-deleted");
        assertFalse(Boolean.TRUE.equals(keptLine.getIsDeleted()), "the kept line must survive");
    }

    @Test
    void shouldAddANewLineAlongsideAnExistingOne() {
        Product first = product(7L, "Kopi Robusta 1kg");
        Product second = product(8L, "Gula Pasir 1kg");
        Supplier supplier = supplier(first, second);

        when(poRepository.doGet(50L))
                .thenReturn(storedOrder(DocStatus.DRAFT, supplier, storedDetail(80L, first, 4)));
        when(supplierRepository.doGet(1L)).thenReturn(supplier);
        echoDetailSaves();

        service.doSubmit(50L, request(detailReq(80L, 7L, 4), detailReq(null, 8L, 2)));

        assertEquals(2, lastDetailSave().size(), "the reconciled list must carry both lines");
    }

    @Test
    void shouldRejectTheSameLineIdTwiceInOneRequest() {
        Product product = product(7L, "Kopi Robusta 1kg");
        Supplier supplier = supplier(product);
        when(poRepository.doGet(50L)).thenReturn(storedOrder(DocStatus.DRAFT, supplier, storedDetail(80L, product, 4)));
        when(supplierRepository.doGet(1L)).thenReturn(supplier);

        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> service.doSubmit(50L, request(detailReq(80L, 7L, 1), detailReq(80L, 7L, 2))));

        // Two instructions for one line leave the intent ambiguous.
        assertEquals("Duplicate detail id 80 in the request", thrown.getMessage());
    }

    @Test
    void shouldRejectALineIdFromAnotherDocument() {
        Product product = product(7L, "Kopi Robusta 1kg");
        Supplier supplier = supplier(product);
        when(poRepository.doGet(50L)).thenReturn(storedOrder(DocStatus.DRAFT, supplier, storedDetail(80L, product, 4)));
        when(supplierRepository.doGet(1L)).thenReturn(supplier);

        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> service.doSubmit(50L, request(detailReq(999L, 7L, 1))));

        assertTrue(thrown.getMessage().contains("999"));
        assertTrue(thrown.getMessage().contains("50"));
    }

    @Test
    void shouldRefuseToSubmitAnApprovedDocument() {
        Product product = product(7L, "Kopi Robusta 1kg");
        Supplier supplier = supplier(product);
        when(poRepository.doGet(50L))
                .thenReturn(storedOrder(DocStatus.APPROVED, supplier, storedDetail(80L, product, 4)));
        when(supplierRepository.doGet(1L)).thenReturn(supplier);

        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> service.doSubmit(50L, request(detailReq(80L, 7L, 4))));

        assertEquals("Only draft or cancelled purchase orders can be submitted", thrown.getMessage());
        verify(poRepository, never()).save(any());
    }

    @Test
    void shouldReopenACancelledDocumentAsDraft() {
        Product product = product(7L, "Kopi Robusta 1kg");
        Supplier supplier = supplier(product);
        PurchaseOrder stored = storedOrder(DocStatus.CANCELLED, supplier, storedDetail(80L, product, 4));

        when(poRepository.doGet(50L)).thenReturn(stored);
        when(supplierRepository.doGet(1L)).thenReturn(supplier);
        echoDetailSaves();

        service.doSubmit(50L, request(detailReq(80L, 7L, 4)));

        assertEquals(DocStatus.DRAFT, stored.getStatus(), "resubmitting a cancelled document revives it");
    }

    // ------------------------------------------------------------------
    // doApprove -- the only place stock moves
    // ------------------------------------------------------------------

    @Test
    void shouldApproveAndRecordAStockInMovement() {
        Product product = product(7L, "Kopi Robusta 1kg");
        Supplier supplier = supplier(product);
        PurchaseOrder stored = storedOrder(DocStatus.DRAFT, supplier, storedDetail(80L, product, 4));
        when(poRepository.doGet(50L)).thenReturn(stored);

        Response response = service.doApprove(50L, request(detailReq(80L, 7L, 4)));

        assertEquals(200, response.getCode());
        assertEquals(DocStatus.APPROVED, stored.getStatus());
        assertEquals(new BigDecimal("6000"), stored.getRealGrandTotal());

        Stock movement = savedStocks().get(0);

        assertEquals(StockActivity.SI, movement.getActivity(), "receiving goods is a stock in");
        assertEquals(DocType.PO, movement.getDocumentType());
        assertEquals("PO/2026/09/0001", movement.getDocumentNumber());
        assertEquals(4, movement.getQuantity());
        assertSame(stored, movement.getPurchaseOrder());
    }

    @Test
    void shouldOpenAStockPositionAtZeroForAProductThatNeverMoved() {
        Product product = product(7L, "Kopi Robusta 1kg");
        Supplier supplier = supplier(product);
        when(poRepository.doGet(50L))
                .thenReturn(storedOrder(DocStatus.DRAFT, supplier, storedDetail(80L, product, 4)));

        service.doApprove(50L, request(detailReq(80L, 7L, 4)));

        assertEquals(4, savedPositions().get(0).getQuantity());
    }

    @Test
    void shouldAddToAnExistingStockPosition() {
        Product product = product(7L, "Kopi Robusta 1kg");
        StockPosition existing = new StockPosition();
        existing.setId(300L);
        existing.setQuantity(10);
        product.setStockPosition(existing);
        // StockPosition.setProduct is deliberately left unset: the generated
        // equals/hashCode of these entities walk their associations, so a
        // Product <-> StockPosition cycle recurses until the stack runs out.
        // The service only reads product.getStockPosition() anyway.

        Supplier supplier = supplier(product);
        when(poRepository.doGet(50L))
                .thenReturn(storedOrder(DocStatus.DRAFT, supplier, storedDetail(80L, product, 4)));

        service.doApprove(50L, request(detailReq(80L, 7L, 4)));

        assertSame(existing, savedPositions().get(0), "the existing row must be updated, not replaced");
        assertEquals(14, existing.getQuantity());
    }

    @Test
    void shouldWriteOneStockPositionForAProductRepeatedAcrossLines() {
        Product product = product(7L, "Kopi Robusta 1kg");
        Supplier supplier = supplier(product);
        PurchaseOrder stored = storedOrder(DocStatus.DRAFT, supplier,
                storedDetail(80L, product, 4), storedDetail(81L, product, 6));
        when(poRepository.doGet(50L)).thenReturn(stored);

        service.doApprove(50L, request(detailReq(80L, 7L, 4), detailReq(81L, 7L, 6)));

        List<StockPosition> positions = savedPositions();

        // One row holding the sum, not two rows racing each other.
        assertEquals(1, positions.size());
        assertEquals(10, positions.get(0).getQuantity());
    }

    @Test
    void shouldRefuseToApproveAnythingButADraft() {
        Product product = product(7L, "Kopi Robusta 1kg");
        when(poRepository.doGet(50L))
                .thenReturn(storedOrder(DocStatus.APPROVED, supplier(product), storedDetail(80L, product, 4)));

        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> service.doApprove(50L, request(detailReq(80L, 7L, 4))));

        assertEquals("Only draft purchase orders can be approved", thrown.getMessage());
        verifyNoInteractions(stockRepository);
        verifyNoInteractions(stockPositionRepository);
    }

    @Test
    void shouldRejectAnApprovalForALineThatIsNotOnTheDocument() {
        Product product = product(7L, "Kopi Robusta 1kg");
        when(poRepository.doGet(50L))
                .thenReturn(storedOrder(DocStatus.DRAFT, supplier(product), storedDetail(80L, product, 4)));

        NotFoundException thrown = assertThrows(NotFoundException.class,
                () -> service.doApprove(50L, request(detailReq(999L, 7L, 1))));

        assertTrue(thrown.getMessage().contains("999"));
        assertTrue(thrown.getMessage().contains("PO/2026/09/0001"));
    }

    @Test
    void shouldRejectReceivingMoreThanWasOrdered() {
        Product product = product(7L, "Kopi Robusta 1kg");
        when(poRepository.doGet(50L))
                .thenReturn(storedOrder(DocStatus.DRAFT, supplier(product), storedDetail(80L, product, 4)));

        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> service.doApprove(50L, request(detailReq(80L, 7L, 5))));

        assertTrue(thrown.getMessage().contains("(5)"));
        assertTrue(thrown.getMessage().contains("(4)"));
        assertTrue(thrown.getMessage().contains("Kopi Robusta 1kg"));
        verifyNoInteractions(stockRepository);
    }

    @Test
    void shouldAllowReceivingLessThanWasOrdered() {
        Product product = product(7L, "Kopi Robusta 1kg");
        PurchaseOrderDetail line = storedDetail(80L, product, 10);
        PurchaseOrder stored = storedOrder(DocStatus.DRAFT, supplier(product), line);
        when(poRepository.doGet(50L)).thenReturn(stored);

        service.doApprove(50L, request(detailReq(80L, 7L, 3)));

        assertEquals(3, line.getRealQuantity());
        assertEquals(new BigDecimal("4500"), line.getRealSubtotal());
        // A short delivery is normal, and the ordered figures stay for comparison.
        assertEquals(10, line.getOrderQuantity());
        assertEquals(new BigDecimal("4500"), stored.getRealGrandTotal());
    }

    @Test
    void shouldKeepTheStoredNoteWhenApprovalSendsNone() {
        Product product = product(7L, "Kopi Robusta 1kg");
        PurchaseOrderDetail line = storedDetail(80L, product, 4);
        line.setNote("from the original order");
        when(poRepository.doGet(50L)).thenReturn(storedOrder(DocStatus.DRAFT, supplier(product), line));

        service.doApprove(50L, request(detailReq(80L, 7L, 4)));

        assertEquals("from the original order", line.getNote());
    }

    @Test
    void shouldOverwriteTheNoteWhenApprovalSendsOne() {
        Product product = product(7L, "Kopi Robusta 1kg");
        PurchaseOrderDetail line = storedDetail(80L, product, 4);
        line.setNote("from the original order");
        when(poRepository.doGet(50L)).thenReturn(storedOrder(DocStatus.DRAFT, supplier(product), line));

        PODetailSubmitReq reqDetail = detailReq(80L, 7L, 4);
        reqDetail.setNote("two crates dented");

        service.doApprove(50L, request(reqDetail));

        assertEquals("two crates dented", line.getNote());
    }

    // ------------------------------------------------------------------
    // doCancel
    // ------------------------------------------------------------------

    @Test
    void shouldCancelADraft() {
        Product product = product(7L, "Kopi Robusta 1kg");
        PurchaseOrder stored = storedOrder(DocStatus.DRAFT, supplier(product), storedDetail(80L, product, 4));
        when(poRepository.doGet(50L)).thenReturn(stored);
        when(poRepository.save(stored)).thenReturn(stored);

        Response response = service.doCancel(50L);

        assertEquals(200, response.getCode());
        assertEquals(DocStatus.CANCELLED, stored.getStatus());
    }

    @Test
    void shouldRefuseToCancelAnythingButADraft() {
        Product product = product(7L, "Kopi Robusta 1kg");
        when(poRepository.doGet(50L))
                .thenReturn(storedOrder(DocStatus.APPROVED, supplier(product), storedDetail(80L, product, 4)));

        BadRequestException thrown = assertThrows(BadRequestException.class, () -> service.doCancel(50L));

        assertEquals("Only draft purchase orders can be cancelled", thrown.getMessage());
        verify(poRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // read and visibility, all delegated to the shared repository
    // ------------------------------------------------------------------

    @Test
    void shouldReadOneDocument() {
        Product product = product(7L, "Kopi Robusta 1kg");
        when(poRepository.doGet(50L))
                .thenReturn(storedOrder(DocStatus.DRAFT, supplier(product), storedDetail(80L, product, 4)));

        Response response = service.doDetail(50L);

        assertEquals(200, response.getCode());
        PORes body = (PORes) response.getData();
        assertEquals("PO/2026/09/0001", body.getDocumentNumber());
        // The supplier and product are flattened so the client needs no second call.
        assertEquals(1L, body.getSupplierId());
        assertEquals("PT Sumber Makmur", body.getSupplierName());
        assertEquals(1, body.getDetails().size());
        assertEquals("Kopi Robusta 1kg", body.getDetails().get(0).getProductName());
        assertEquals(7L, body.getDetails().get(0).getProductId());
    }

    @Test
    void shouldActivate() {
        Product product = product(7L, "Kopi Robusta 1kg");
        when(poRepository.doActivate(50L))
                .thenReturn(storedOrder(DocStatus.DRAFT, supplier(product), storedDetail(80L, product, 4)));

        assertEquals(200, service.doActivate(50L).getCode());
        verify(poRepository).doActivate(50L);
    }

    @Test
    void shouldDeactivate() {
        Product product = product(7L, "Kopi Robusta 1kg");
        when(poRepository.doDeactivate(50L))
                .thenReturn(storedOrder(DocStatus.DRAFT, supplier(product), storedDetail(80L, product, 4)));

        assertEquals(200, service.doDeactivate(50L).getCode());
        verify(poRepository).doDeactivate(50L);
    }

    @Test
    void shouldDelete() {
        Product product = product(7L, "Kopi Robusta 1kg");
        when(poRepository.doDelete(50L))
                .thenReturn(storedOrder(DocStatus.DRAFT, supplier(product), storedDetail(80L, product, 4)));

        assertEquals(200, service.doDelete(50L).getCode());
        verify(poRepository).doDelete(50L);
    }

    // ------------------------------------------------------------------
    // doSearch
    // ------------------------------------------------------------------

    @Test
    void shouldSearchAndMapEveryRow() {
        Product product = product(7L, "Kopi Robusta 1kg");
        Page<PurchaseOrder> page = new PageImpl<>(List.of(
                storedOrder(DocStatus.DRAFT, supplier(product), storedDetail(80L, product, 4))));

        when(poRepository.doSearch(any(), any(POSearchReq.class))).thenReturn(page);

        PagingResponse response = service.doSearch(new POSearchReq());

        assertEquals(200, response.getCode());
        assertEquals(1L, response.getTotalRecord());
        assertEquals(1, ((List<?>) response.getData()).size());
    }

    @Test
    void shouldWrapAFailedSearchAsAServiceException() {
        when(poRepository.doSearch(any(), any(POSearchReq.class)))
                .thenThrow(new IllegalStateException("could not extract ResultSet"));

        ServiceException thrown = assertThrows(ServiceException.class, () -> service.doSearch(new POSearchReq()));

        // A 500 for the caller, with the cause kept in the message for the log.
        assertEquals("could not extract ResultSet", thrown.getMessage());
    }

    @Test
    void shouldSearchWithASpecificationBuiltFromTheRequest() {
        when(poRepository.doSearch(any(), any(POSearchReq.class))).thenReturn(new PageImpl<>(List.of()));

        POSearchReq req = new POSearchReq();
        req.setDocumentNumber("PO/2026");

        service.doSearch(req);

        ArgumentCaptor<Specification<PurchaseOrder>> spec = ArgumentCaptor.forClass(Specification.class);
        verify(poRepository).doSearch(spec.capture(), any(POSearchReq.class));
        assertNotNull(spec.getValue(), "the DAO must always produce a specification");
    }

    // ------------------------------------------------------------------
    // captors, kept here so the generics noise stays out of the tests
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<PurchaseOrderDetail> lastDetailSave() {
        ArgumentCaptor<Iterable<PurchaseOrderDetail>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(poDetailRepository, org.mockito.Mockito.atLeastOnce()).saveAll(captor.capture());

        // syncDetails saves the removals first, so the reconciled list is last.
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
        // Captured as an Iterable on purpose: doApprove hands over a Map's values
        // view, which is a Collection but not a List.
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
