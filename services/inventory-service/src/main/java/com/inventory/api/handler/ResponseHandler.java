package com.inventory.api.handler;

import com.inventory.api.constant.ResponseMsg;
import com.inventory.api.exception.BadRequestException;
import com.inventory.api.exception.ForbiddenException;
import com.inventory.api.exception.NotFoundException;
import com.inventory.api.exception.ServiceException;
import com.inventory.api.model.dto.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@ControllerAdvice
public class ResponseHandler {

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<Response> handlingServiceExc(ServiceException exc, WebRequest req) {
        return new ResponseEntity<>(
                generateResponse(req, HttpStatus.INTERNAL_SERVER_ERROR.value(), ResponseMsg.INTERNAL_SERVER_ERROR, exc.getMessage()),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Response> handlingBadReqExc(BadRequestException exc, WebRequest req) {
        return new ResponseEntity<>(
                generateResponse(req, HttpStatus.BAD_REQUEST.value(), ResponseMsg.BAD_REQUEST, exc.getMessage()),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Response> handlingNotFoundExc(NotFoundException exc, WebRequest req) {
        return new ResponseEntity<>(
                generateResponse(req, HttpStatus.NOT_FOUND.value(), ResponseMsg.NOT_FOUND, exc.getMessage()),
                HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Response> handlingForbiddenExc(ForbiddenException exc, WebRequest req) {
        return new ResponseEntity<>(
                generateResponse(req, HttpStatus.FORBIDDEN.value(), ResponseMsg.FORBIDDEN, exc.getMessage()),
                HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Response> handlingValidationExc(MethodArgumentNotValidException exc, WebRequest req) {
        String message = exc.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .reduce((m1, m2) -> m1 + ", " + m2)
                .orElse("Invalid request");

        return new ResponseEntity<>(
                generateResponse(req, HttpStatus.BAD_REQUEST.value(), ResponseMsg.BAD_REQUEST, message),
                HttpStatus.BAD_REQUEST);
    }

    private Response generateResponse(WebRequest req, int code, String status, String msg) {
        return new Response(code, status, generateObject(req, code, msg));
    }

    private Map<String, Object> generateObject(WebRequest req, int code, String msg) {
        log.error(msg);
        if (code == 500) {
            msg = "There are some internal server error, please contact the administrator";
        }

        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", LocalDateTime.now());
        result.put("status", code);
        result.put("error", msg);
        result.put("path", req.getDescription(false));
        return result;
    }
}
