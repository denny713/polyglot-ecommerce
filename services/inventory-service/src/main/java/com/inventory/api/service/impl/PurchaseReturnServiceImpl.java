package com.inventory.api.service.impl;

import com.inventory.api.constant.ResponseMsg;
import com.inventory.api.dao.PurchaseReturnDao;
import com.inventory.api.exception.ServiceException;
import com.inventory.api.model.dto.request.pr.PRSearchReq;
import com.inventory.api.model.dto.request.pr.PRSubmitReq;
import com.inventory.api.model.dto.response.PagingResponse;
import com.inventory.api.model.dto.response.Response;
import com.inventory.api.model.dto.response.pr.PRDetailRes;
import com.inventory.api.model.dto.response.pr.PRRes;
import com.inventory.api.model.entity.PurchaseReturn;
import com.inventory.api.model.entity.PurchaseReturnDetail;
import com.inventory.api.repository.*;
import com.inventory.api.service.PurchaseReturnService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

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
        return null;
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
    public Response doApprove(Long id, PRSubmitReq req) {
        return null;
    }

    @Override
    @Transactional
    public Response doCancel(Long id) {
        return null;
    }

    @Override
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
