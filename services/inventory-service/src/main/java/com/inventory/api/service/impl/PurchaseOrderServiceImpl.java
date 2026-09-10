package com.inventory.api.service.impl;

import com.inventory.api.constant.ResponseMsg;
import com.inventory.api.dao.PurchaseOrderDao;
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
import com.inventory.api.model.dto.response.po.PODetailRes;
import com.inventory.api.model.dto.response.po.PORes;
import com.inventory.api.model.entity.*;
import com.inventory.api.repository.*;
import com.inventory.api.service.PurchaseOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implements the purchase order lifecycle.
 * <p>
 * Two things are worth knowing before changing this class.
 * <p>
 * Submitting reconciles rather than replaces. The details in the request are
 * matched against what is stored: lines with an id are updated, lines without one
 * are added, and stored lines the request left out are soft-deleted. That is why a
 * duplicated id in one request is rejected — it would make the intent ambiguous.
 * <p>
 * Approving is the only place stock moves. It writes the ledger rows and updates
 * the per-product positions in the same transaction as the status change, so the
 * document and the stock level cannot disagree. Positions are accumulated in a map
 * first, so a product appearing on several lines is written once rather than
 * overwritten per line.
 * <p>
 * Prices are read from the product at submit time and copied onto the line, so a
 * later price change does not rewrite an existing document.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private final DocumentNumberRepository docNoRepository;
    private final PurchaseOrderRepository poRepository;
    private final PurchaseOrderDetailRepository poDetailRepository;
    private final SupplierRepository supplierRepository;
    private final StockRepository stockRepository;
    private final StockPositionRepository stockPositionRepository;

    @Override
    @Transactional
    public Response doSubmit(Long id, POSubmitReq req) {
        boolean isNew = (id == null);
        PurchaseOrder po = isNew ? new PurchaseOrder() : poRepository.doGet(id);
        Supplier supplier = supplierRepository.doGet(req.getSupplierId());

        if (isNew) {
            po.setStatus(DocStatus.DRAFT);
            po.setDocumentNumber(docNoRepository.generateDocumentNumber(DocType.PO.name(),
                    LocalDate.now(ZoneId.of("Asia/Jakarta"))));
        }

        if (Objects.equals(po.getStatus(), DocStatus.APPROVED)) {
            throw new BadRequestException("Only draft or cancelled purchase orders can be submitted");
        }

        po.setSupplier(supplier);
        po.setNote(StringUtils.isEmpty(req.getNote()) ? "-" : req.getNote());
        if (Objects.equals(po.getStatus(), DocStatus.CANCELLED)) {
            po.setStatus(DocStatus.DRAFT);
        }

        List<PurchaseOrderDetail> details = syncDetails(po, req.getDetails());

        po.setRealGrandTotal(BigDecimal.ZERO);
        po.setOrderGrandTotal(details.stream()
                .map(PurchaseOrderDetail::getOrderSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        poRepository.save(po);
        po.setPurchaseOrderDetails(poDetailRepository.saveAll(details));

        return new Response(isNew ? 201 : 200, ResponseMsg.SUCCESS, setPOResponse(po));
    }

    @Override
    @Transactional
    public Response doDetail(Long id) {
        return new Response(200, ResponseMsg.SUCCESS, setPOResponse(poRepository.doGet(id)));
    }

    @Override
    @Transactional
    public Response doActivate(Long id) {
        return new Response(200, ResponseMsg.SUCCESS, setPOResponse(poRepository.doActivate(id)));
    }

    @Override
    @Transactional
    public Response doDeactivate(Long id) {
        return new Response(200, ResponseMsg.SUCCESS, setPOResponse(poRepository.doDeactivate(id)));
    }

    @Override
    @Transactional
    public Response doDelete(Long id) {
        return new Response(200, ResponseMsg.SUCCESS, setPOResponse(poRepository.doDelete(id)));
    }

    @Override
    @Transactional
    public Response doApprove(Long id, POSubmitReq req) {
        PurchaseOrder po = poRepository.doGet(id);

        if (!Objects.equals(po.getStatus(), DocStatus.DRAFT)) {
            throw new BadRequestException("Only draft purchase orders can be approved");
        }

        Map<Long, PurchaseOrderDetail> existing = getExistingDetails(po);
        Map<Long, StockPosition> positions = new LinkedHashMap<>();

        List<PurchaseOrderDetail> details = new ArrayList<>(req.getDetails().size());
        List<Stock> stocks = new ArrayList<>(req.getDetails().size());
        BigDecimal realGrandTotal = BigDecimal.ZERO;

        for (PODetailSubmitReq reqDetail : req.getDetails()) {
            PurchaseOrderDetail detail = approveDetail(po, reqDetail, existing);

            details.add(detail);
            stocks.add(buildStock(po, reqDetail, detail));
            addStockPosition(positions, reqDetail, detail);

            realGrandTotal = realGrandTotal.add(detail.getRealSubtotal());
        }

        po.setStatus(DocStatus.APPROVED);
        po.setRealGrandTotal(realGrandTotal);
        po.setPurchaseOrderDetails(details);

        stockRepository.saveAll(stocks);
        stockPositionRepository.saveAll(positions.values());

        return new Response(200, ResponseMsg.SUCCESS, setPOResponse(po));
    }

    @Override
    @Transactional
    public Response doCancel(Long id) {
        PurchaseOrder po = poRepository.doGet(id);

        if (!Objects.equals(po.getStatus(), DocStatus.DRAFT)) {
            throw new BadRequestException("Only draft purchase orders can be cancelled");
        }

        po.setStatus(DocStatus.CANCELLED);

        return new Response(200, ResponseMsg.SUCCESS, setPOResponse(poRepository.save(po)));
    }

    @Override
    @Transactional
    public PagingResponse doSearch(POSearchReq req) {
        try {
            PurchaseOrderDao poDao = new PurchaseOrderDao();
            List<PORes> results = new ArrayList<>();

            Page<PurchaseOrder> pos = poRepository.doSearch(poDao.buildSearchPO(req), req);
            pos.forEach(x -> results.add(setPOResponse(x)));

            return new PagingResponse(200, ResponseMsg.SUCCESS, results,
                    pos.getTotalElements(), pos.getTotalElements());
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new ServiceException(e.getMessage());
        }
    }

    private List<PurchaseOrderDetail> syncDetails(PurchaseOrder po, List<PODetailSubmitReq> reqDetails) {
        if (reqDetails.isEmpty()) {
            throw new BadRequestException("Details cannot be empty");
        }

        Map<Long, Product> products = getAndSyncProducts(reqDetails, po.getSupplier());
        Map<Long, PurchaseOrderDetail> existing = getExistingDetails(po);
        Set<Long> keptIds = new HashSet<>();

        List<PurchaseOrderDetail> details = new ArrayList<>(reqDetails.size());
        for (PODetailSubmitReq reqDetail : reqDetails) {
            details.add(buildDetail(po, reqDetail, products, existing, keptIds));
        }

        List<PurchaseOrderDetail> removed = existing.values().stream()
                .filter(detail -> !keptIds.contains(detail.getId())).toList();

        removed.forEach(PurchaseOrderDetail::doDelete);
        poDetailRepository.saveAll(removed);

        return details;
    }

    private Map<Long, Product> getAndSyncProducts(List<PODetailSubmitReq> reqDetails, Supplier supplier) {
        List<Long> productIds = reqDetails.stream().map(PODetailSubmitReq::getProductId).distinct().toList();
        Map<Long, Product> products = supplier.getProducts().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<Long> missingIds = productIds.stream().filter(id -> !products.containsKey(id)).toList();
        if (!missingIds.isEmpty()) {
            throw new NotFoundException(String.format(
                    "Data Product with id %s not found in supplier %s",
                    missingIds, supplier.getName()));
        }

        return products;
    }

    private Map<Long, PurchaseOrderDetail> getExistingDetails(PurchaseOrder po) {
        return (po.getId() == null) ? Map.of()
                : po.getPurchaseOrderDetails().stream()
                .collect(Collectors.toMap(PurchaseOrderDetail::getId, Function.identity()));
    }

    private PurchaseOrderDetail approveDetail(
            PurchaseOrder po, PODetailSubmitReq reqDetail,
            Map<Long, PurchaseOrderDetail> existing) {
        PurchaseOrderDetail detail = existing.get(reqDetail.getId());

        if (detail == null) {
            throw new NotFoundException(String.format(
                    "Detail with id %s not found in purchase order %s",
                    reqDetail.getId(), po.getDocumentNumber()));
        }

        if (reqDetail.getQuantity() > detail.getOrderQuantity()) {
            throw new BadRequestException(String.format(
                    "Real quantity (%s) cannot be greater than order quantity (%s) for product %s",
                    reqDetail.getQuantity(), detail.getOrderQuantity(), detail.getProduct().getName()));
        }

        detail.setRealQuantity(reqDetail.getQuantity());
        detail.setRealSubtotal(detail.getProduct().getPrice().multiply(BigDecimal.valueOf(reqDetail.getQuantity())));
        detail.setNote(StringUtils.isEmpty(reqDetail.getNote()) ? detail.getNote() : reqDetail.getNote());

        return detail;
    }

    private Stock buildStock(PurchaseOrder po, PODetailSubmitReq reqDetail, PurchaseOrderDetail detail) {
        Stock stock = new Stock();
        stock.setProduct(detail.getProduct());
        stock.setDocumentNumber(po.getDocumentNumber());
        stock.setDocumentType(DocType.PO);
        stock.setActivity(StockActivity.SI);
        stock.setQuantity(reqDetail.getQuantity());
        stock.setPurchaseOrder(po);

        return stock;
    }

    private void addStockPosition(
            Map<Long, StockPosition> positions,
            PODetailSubmitReq reqDetail, PurchaseOrderDetail detail) {
        Product product = detail.getProduct();
        StockPosition position = positions.computeIfAbsent(
                product.getId(), productId -> resolveStockPosition(product));

        position.setQuantity(position.getQuantity() + reqDetail.getQuantity());
    }

    private StockPosition resolveStockPosition(Product product) {
        if (product.getStockPosition() != null) {
            return product.getStockPosition();
        }

        StockPosition position = new StockPosition();
        position.setProduct(product);
        position.setQuantity(0);

        return position;
    }

    private PurchaseOrderDetail buildDetail(
            PurchaseOrder po, PODetailSubmitReq reqDetail,
            Map<Long, Product> products,
            Map<Long, PurchaseOrderDetail> existing,
            Set<Long> keptIds) {
        PurchaseOrderDetail detail = resolveDetail(reqDetail, existing, keptIds, po.getId());
        Product product = products.get(reqDetail.getProductId());
        BigDecimal price = product.getPrice();

        detail.setPurchaseOrder(po);
        detail.setProduct(product);
        detail.setRealQuantity(0);
        detail.setOrderQuantity(reqDetail.getQuantity());
        detail.setNote(reqDetail.getNote());
        detail.setUnitPrice(price);
        detail.setRealSubtotal(BigDecimal.ZERO);
        detail.setOrderSubtotal(price.multiply(BigDecimal.valueOf(reqDetail.getQuantity())));

        return detail;
    }

    private PurchaseOrderDetail resolveDetail(
            PODetailSubmitReq reqDetail,
            Map<Long, PurchaseOrderDetail> existing,
            Set<Long> keptIds, Long poId) {
        if (reqDetail.getId() == null) {
            PurchaseOrderDetail detail = new PurchaseOrderDetail();
            detail.setRealQuantity(0);

            return detail;
        }

        if (!keptIds.add(reqDetail.getId())) {
            throw new BadRequestException(String.format("Duplicate detail id %s in the request", reqDetail.getId()));
        }

        PurchaseOrderDetail detail = existing.get(reqDetail.getId());
        if (detail == null) {
            throw new BadRequestException(String.format("Detail with id %s does not belong to purchase order %s",
                    reqDetail.getId(), poId));
        }

        return detail;
    }

    private PORes setPOResponse(PurchaseOrder po) {
        PORes res = new PORes();

        BeanUtils.copyProperties(po, res);
        res.setSupplierId(po.getSupplier().getId());
        res.setSupplierName(po.getSupplier().getName());
        res.setDetails(po.getPurchaseOrderDetails().stream().map(this::setPODetailResponse).toList());

        return res;
    }

    private PODetailRes setPODetailResponse(PurchaseOrderDetail detail) {
        PODetailRes res = new PODetailRes();

        BeanUtils.copyProperties(detail, res);
        res.setProductId(detail.getProduct().getId());
        res.setProductName(detail.getProduct().getName());

        return res;
    }
}
