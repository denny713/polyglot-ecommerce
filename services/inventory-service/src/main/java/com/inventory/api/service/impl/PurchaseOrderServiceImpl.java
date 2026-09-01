package com.inventory.api.service.impl;

import com.inventory.api.constant.ResponseMsg;
import com.inventory.api.enums.DocStatus;
import com.inventory.api.enums.DocType;
import com.inventory.api.exception.BadRequestException;
import com.inventory.api.exception.NotFoundException;
import com.inventory.api.model.dto.request.po.PODetailSubmitReq;
import com.inventory.api.model.dto.request.po.POSubmitReq;
import com.inventory.api.model.dto.response.Response;
import com.inventory.api.model.dto.response.po.PODetailSubmitRes;
import com.inventory.api.model.dto.response.po.POSubmitRes;
import com.inventory.api.model.entity.Product;
import com.inventory.api.model.entity.PurchaseOrder;
import com.inventory.api.model.entity.PurchaseOrderDetail;
import com.inventory.api.repository.*;
import com.inventory.api.service.PurchaseOrderService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private final PurchaseOrderRepository poRepository;
    private final PurchaseOrderDetailRepository poDetailRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final DocumentNumberRepository docNoRepository;

    @Override
    @Transactional
    public Response doSubmit(Long id, POSubmitReq req) {
        boolean isNew = (id == null);
        PurchaseOrder po = isNew ? new PurchaseOrder() : poRepository.doGet(id);

        if (isNew) {
            po.setStatus(DocStatus.DRAFT);
            po.setDocumentNumber(docNoRepository.generateDocumentNumber(DocType.PO.name(),
                    LocalDate.now(ZoneId.of("Asia/Jakarta"))));
        }

        if (po.getStatus() == DocStatus.APPROVED) {
            throw new BadRequestException("Only draft or cancelled purchase orders can be submitted");
        }

        po.setSupplier(supplierRepository.doGet(req.getSupplierId()));
        po.setNote(StringUtils.isEmpty(req.getNote()) ? "-" : req.getNote());
        if (po.getStatus() == DocStatus.CANCELLED) {
            po.setStatus(DocStatus.DRAFT);
        }

        List<PurchaseOrderDetail> details = syncDetails(po, req.getDetails());

        po.setGrandTotal(details.stream()
                .map(PurchaseOrderDetail::getSubtotal)
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

    private List<PurchaseOrderDetail> syncDetails(PurchaseOrder po, List<PODetailSubmitReq> reqDetails) {
        Map<Long, Product> products = getProducts(reqDetails);
        Map<Long, PurchaseOrderDetail> existing = getExistingDetails(po);
        Set<Long> keptIds = new HashSet<>();

        List<PurchaseOrderDetail> details = new ArrayList<>(reqDetails.size());
        for (PODetailSubmitReq reqDetail : reqDetails) {
            details.add(buildDetail(po, reqDetail, products, existing, keptIds));
        }

        List<PurchaseOrderDetail> removed = existing.values().stream()
                .filter(detail -> !keptIds.contains(detail.getId()))
                .toList();

        removed.forEach(PurchaseOrderDetail::doDelete);
        poDetailRepository.saveAll(removed);

        return details;
    }

    private Map<Long, Product> getProducts(List<PODetailSubmitReq> reqDetails) {
        List<Long> productIds = reqDetails.stream()
                .map(PODetailSubmitReq::getProductId).distinct().toList();

        Map<Long, Product> products = productRepository.doList(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<Long> missingIds = productIds.stream()
                .filter(productId -> !products.containsKey(productId)).toList();

        if (!missingIds.isEmpty()) {
            throw new NotFoundException(String.format(
                    "Data Product with id %s not found", missingIds));
        }

        return products;
    }

    private Map<Long, PurchaseOrderDetail> getExistingDetails(PurchaseOrder po) {
        return (po.getId() == null) ? Map.of()
                : po.getPurchaseOrderDetails().stream()
                .collect(Collectors.toMap(PurchaseOrderDetail::getId, Function.identity()));
    }

    private PurchaseOrderDetail buildDetail(PurchaseOrder po, PODetailSubmitReq reqDetail,
                                            Map<Long, Product> products,
                                            Map<Long, PurchaseOrderDetail> existing,
                                            Set<Long> keptIds) {
        PurchaseOrderDetail detail = resolveDetail(reqDetail, existing, keptIds, po.getId());
        Product product = products.get(reqDetail.getProductId());
        BigDecimal price = product.getPrice();

        detail.setPurchaseOrder(po);
        detail.setProduct(product);
        detail.setOrderQuantity(reqDetail.getOrderQuantity());
        detail.setNote(reqDetail.getNote());
        detail.setUnitPrice(price);
        detail.setSubtotal(price.multiply(BigDecimal.valueOf(reqDetail.getOrderQuantity())));

        return detail;
    }

    private PurchaseOrderDetail resolveDetail(PODetailSubmitReq reqDetail,
                                              Map<Long, PurchaseOrderDetail> existing,
                                              Set<Long> keptIds, Long poId) {
        if (reqDetail.getId() == null) {
            PurchaseOrderDetail detail = new PurchaseOrderDetail();
            detail.setRealQuantity(0);

            return detail;
        }

        if (!keptIds.add(reqDetail.getId())) {
            throw new BadRequestException(String.format(
                    "Duplicate detail id %s in the request", reqDetail.getId()));
        }

        PurchaseOrderDetail detail = existing.get(reqDetail.getId());
        if (detail == null) {
            throw new BadRequestException(String.format(
                    "Detail with id %s does not belong to purchase order %s",
                    reqDetail.getId(), poId));
        }

        return detail;
    }

    public POSubmitRes setPOResponse(PurchaseOrder po) {
        POSubmitRes res = new POSubmitRes();

        BeanUtils.copyProperties(po, res);
        res.setSupplierId(po.getSupplier().getId());
        res.setSupplierName(po.getSupplier().getName());
        res.setDetails(po.getPurchaseOrderDetails().stream()
                .map(this::setPODetailResponse)
                .toList());

        return res;
    }

    private PODetailSubmitRes setPODetailResponse(PurchaseOrderDetail detail) {
        PODetailSubmitRes res = new PODetailSubmitRes();

        BeanUtils.copyProperties(detail, res);
        res.setProductId(detail.getProduct().getId());
        res.setProductName(detail.getProduct().getName());

        return res;
    }
}
