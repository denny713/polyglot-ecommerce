package com.inventory.api.service.impl;

import com.inventory.api.constant.ResponseMsg;
import com.inventory.api.dao.PurchaseReturnDao;
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
import com.inventory.api.model.dto.response.pr.PRDetailRes;
import com.inventory.api.model.dto.response.pr.PRRes;
import com.inventory.api.model.entity.*;
import com.inventory.api.repository.*;
import com.inventory.api.service.PurchaseReturnService;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseReturnServiceImpl implements PurchaseReturnService {

    private final DocumentNumberRepository docNoRepository;
    private final PurchaseReturnRepository prRepository;
    private final PurchaseReturnDetailRepository prDetailRepository;
    private final SupplierRepository supplierRepository;
    private final StockRepository stockRepository;
    private final StockPositionRepository stockPositionRepository;

    @Override
    @Transactional
    public Response doSubmit(Long id, PRSubmitReq req) {
        boolean isNew = (id == null);
        PurchaseReturn pr = isNew ? new PurchaseReturn() : prRepository.doGet(id);
        Supplier supplier = supplierRepository.doGet(req.getSupplierId());

        if (isNew) {
            pr.setStatus(DocStatus.DRAFT);
            pr.setDocumentNumber(docNoRepository.generateDocumentNumber(DocType.PR.name(),
                    LocalDate.now(ZoneId.of("Asia/Jakarta"))));
        }

        if (Objects.equals(pr.getStatus(), DocStatus.APPROVED)) {
            throw new BadRequestException("Only draft or cancelled purchase return can be submitted");
        }

        pr.setSupplier(supplier);
        pr.setReason(StringUtils.isEmpty(req.getReason()) ? "-" : req.getReason());
        pr.setNote(StringUtils.isEmpty(req.getNote()) ? "-" : req.getNote());
        if (Objects.equals(pr.getStatus(), DocStatus.CANCELLED)) {
            pr.setStatus(DocStatus.DRAFT);
        }

        List<PurchaseReturnDetail> details = syncDetails(pr, req.getDetails());

        pr.setGrandTotal(details.stream()
                .map(PurchaseReturnDetail::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        prRepository.save(pr);
        pr.setPurchaseReturnDetails(prDetailRepository.saveAll(details));

        return new Response(isNew ? 201 : 200, ResponseMsg.SUCCESS, setPRResponse(pr));
    }

    @Override
    @Transactional
    public Response doDetail(Long id) {
        return new Response(200, ResponseMsg.SUCCESS, setPRResponse(prRepository.doGet(id)));
    }

    @Override
    @Transactional
    public Response doActivate(Long id) {
        return new Response(200, ResponseMsg.SUCCESS, setPRResponse(prRepository.doActivate(id)));
    }

    @Override
    @Transactional
    public Response doDeactivate(Long id) {
        return new Response(200, ResponseMsg.SUCCESS, setPRResponse(prRepository.doDeactivate(id)));
    }

    @Override
    @Transactional
    public Response doDelete(Long id) {
        return new Response(200, ResponseMsg.SUCCESS, setPRResponse(prRepository.doDelete(id)));
    }

    @Override
    @Transactional
    public Response doApprove(Long id) {
        PurchaseReturn pr = prRepository.doGet(id);

        if (!Objects.equals(pr.getStatus(), DocStatus.DRAFT)) {
            throw new BadRequestException("Only draft purchase return can be approved");
        }

        List<PurchaseReturnDetail> details = pr.getPurchaseReturnDetails();
        Map<Long, StockPosition> positions = new LinkedHashMap<>();
        List<Stock> stocks = new ArrayList<>(details.size());
        BigDecimal grandTotal = BigDecimal.ZERO;

        for (PurchaseReturnDetail detail : details) {
            deductStockPosition(positions, detail);
            stocks.add(buildStock(pr, detail));

            grandTotal = grandTotal.add(detail.getSubtotal());
        }

        pr.setStatus(DocStatus.APPROVED);
        pr.setGrandTotal(grandTotal);

        stockRepository.saveAll(stocks);
        stockPositionRepository.saveAll(positions.values());

        return new Response(200, ResponseMsg.SUCCESS, setPRResponse(pr));
    }

    @Override
    @Transactional
    public Response doCancel(Long id) {
        PurchaseReturn pr = prRepository.doGet(id);

        if (!Objects.equals(pr.getStatus(), DocStatus.DRAFT)) {
            throw new BadRequestException("Only draft purchase return can be canceled");
        }

        pr.setStatus(DocStatus.CANCELLED);

        return new Response(200, ResponseMsg.SUCCESS, setPRResponse(prRepository.save(pr)));
    }

    @Override
    @Transactional
    public PagingResponse doSearch(PRSearchReq req) {
        try {
            PurchaseReturnDao prDao = new PurchaseReturnDao();
            List<PRRes> results = new ArrayList<>();

            Page<PurchaseReturn> prs = prRepository.doSearch(prDao.buildSearchPR(req), req);
            prs.forEach(x -> results.add(setPRResponse(x)));

            return new PagingResponse(200, ResponseMsg.SUCCESS, results,
                    prs.getTotalElements(), prs.getTotalElements());
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new ServiceException(e.getMessage());
        }
    }

    private List<PurchaseReturnDetail> syncDetails(PurchaseReturn pr, List<PRDetailSubmitReq> reqDetails) {
        if (reqDetails.isEmpty()) {
            throw new BadRequestException("Details cannot be empty");
        }

        Map<Long, Product> products = getAndSyncProducts(reqDetails, pr.getSupplier());
        Map<Long, PurchaseReturnDetail> existing = getExistingDetails(pr);
        Set<Long> keptIds = new HashSet<>();

        List<PurchaseReturnDetail> details = new ArrayList<>(reqDetails.size());
        for (PRDetailSubmitReq reqDetail : reqDetails) {
            details.add(buildDetail(pr, reqDetail, products, existing, keptIds));
        }

        List<PurchaseReturnDetail> removed = existing.values().stream()
                .filter(detail -> !keptIds.contains(detail.getId())).toList();

        removed.forEach(PurchaseReturnDetail::doDelete);
        prDetailRepository.saveAll(removed);

        return details;
    }

    private Map<Long, Product> getAndSyncProducts(List<PRDetailSubmitReq> reqDetails, Supplier supplier) {
        List<Long> productIds = reqDetails.stream().map(PRDetailSubmitReq::getProductId).distinct().toList();
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

    private Map<Long, PurchaseReturnDetail> getExistingDetails(PurchaseReturn pr) {
        return (pr.getId() == null) ? Map.of()
                : pr.getPurchaseReturnDetails().stream()
                .collect(Collectors.toMap(PurchaseReturnDetail::getId, Function.identity()));
    }

    private Stock buildStock(PurchaseReturn pr, PurchaseReturnDetail detail) {
        Stock stock = new Stock();
        stock.setProduct(detail.getProduct());
        stock.setDocumentNumber(pr.getDocumentNumber());
        stock.setDocumentType(DocType.PR);
        stock.setActivity(StockActivity.SO);
        stock.setQuantity(detail.getQuantity());
        stock.setPurchaseReturn(pr);

        return stock;
    }

    private void deductStockPosition(Map<Long, StockPosition> positions, PurchaseReturnDetail detail) {
        Product product = detail.getProduct();
        StockPosition position = positions.computeIfAbsent(product.getId(), id -> resolveStockPosition(product));

        int remaining = position.getQuantity() - detail.getQuantity();
        if (remaining < 0) {
            throw new BadRequestException(String.format(
                    "Return quantity (%s) cannot be greater than stock quantity (%s) for product %s",
                    detail.getQuantity(), position.getQuantity(), product.getName()));
        }

        position.setQuantity(remaining);
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

    private PurchaseReturnDetail buildDetail(
            PurchaseReturn pr, PRDetailSubmitReq reqDetail,
            Map<Long, Product> products,
            Map<Long, PurchaseReturnDetail> existing,
            Set<Long> keptIds) {
        PurchaseReturnDetail detail = resolveDetail(reqDetail, existing, keptIds, pr.getId());
        Product product = products.get(reqDetail.getProductId());
        BigDecimal price = product.getPrice();

        detail.setPurchaseReturn(pr);
        detail.setProduct(product);
        detail.setQuantity(reqDetail.getQuantity());
        detail.setUnitPrice(price);
        detail.setSubtotal(price.multiply(BigDecimal.valueOf(reqDetail.getQuantity())));
        detail.setReason(StringUtils.isEmpty(reqDetail.getReason()) ? "-" : reqDetail.getReason());
        detail.setNote(StringUtils.isEmpty(reqDetail.getNote()) ? "-" : reqDetail.getNote());

        return detail;
    }

    private PurchaseReturnDetail resolveDetail(
            PRDetailSubmitReq reqDetail,
            Map<Long, PurchaseReturnDetail> existing,
            Set<Long> keptIds, Long prId) {
        if (reqDetail.getId() == null) {
            PurchaseReturnDetail detail = new PurchaseReturnDetail();
            detail.setQuantity(0);

            return detail;
        }

        if (!keptIds.add(reqDetail.getId())) {
            throw new BadRequestException(String.format("Duplicate detail id %s in the request", reqDetail.getId()));
        }

        PurchaseReturnDetail detail = existing.get(reqDetail.getId());
        if (detail == null) {
            throw new BadRequestException(String.format(
                    "Detail with id %s does not belong to purchase return %s",
                    reqDetail.getId(), prId));
        }

        return detail;
    }

    private PRRes setPRResponse(PurchaseReturn po) {
        PRRes res = new PRRes();

        BeanUtils.copyProperties(po, res);
        res.setSupplierId(po.getSupplier().getId());
        res.setSupplierName(po.getSupplier().getName());
        res.setDetails(po.getPurchaseReturnDetails().stream().map(this::setPRDetailResponse).toList());

        return res;
    }

    private PRDetailRes setPRDetailResponse(PurchaseReturnDetail detail) {
        PRDetailRes res = new PRDetailRes();

        BeanUtils.copyProperties(detail, res);
        res.setProductId(detail.getProduct().getId());
        res.setProductName(detail.getProduct().getName());

        return res;
    }
}
