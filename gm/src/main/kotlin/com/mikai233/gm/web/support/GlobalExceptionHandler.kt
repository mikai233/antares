package com.mikai233.gm.web.support

import com.mikai233.common.annotation.AllOpen
import com.mikai233.gm.web.dto.ApiErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.support.MissingServletRequestPartException

@AllOpen
@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(
        IllegalArgumentException::class,
        MethodArgumentNotValidException::class,
        MethodArgumentTypeMismatchException::class,
        MissingServletRequestParameterException::class,
        MissingServletRequestPartException::class,
        ValidateException::class,
    )
    fun handleBadRequest(ex: Exception): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                ApiErrorResponse(
                    HttpStatus.BAD_REQUEST.value(),
                    HttpStatus.BAD_REQUEST.reasonPhrase,
                    ex.localizedMessage,
                ),
            )
    }

    @ExceptionHandler(Throwable::class)
    fun handleInternalError(ex: Throwable): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                ApiErrorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase,
                    ex.localizedMessage ?: ex.javaClass.simpleName,
                ),
            )
    }
}
