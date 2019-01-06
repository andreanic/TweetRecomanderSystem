package it.keyover.trsserver.exception;

import javax.ws.rs.core.Response;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import it.keyover.trsserver.model.ApiBaseResponse;

@ControllerAdvice
public class ExceptionHandlerController extends ResponseEntityExceptionHandler {
	
    @ExceptionHandler(value = { AppException.class, IllegalStateException.class })
    protected ResponseEntity<Object> handleConflict(AppException ex, WebRequest request) {
        ApiBaseResponse<String> apr = new ApiBaseResponse<String>(((ServletWebRequest)request).getRequest().getRequestURI().toString(), Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), ApiBaseResponse.FAIL, ex.getHrMessage());
        return handleExceptionInternal(ex, apr, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }
}
