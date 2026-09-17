package com.maycol.bancoias.infrastructure.input.rest;

import com.maycol.bancoias.application.dto.TransferRequest;
import com.maycol.bancoias.application.dto.TransferResponse;
import com.maycol.bancoias.application.handler.ITransferHandler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferRestController {

  private final ITransferHandler transferHandler;

  @PostMapping
  public Mono<ResponseEntity<TransferResponse>> create(
    @Valid @RequestBody TransferRequest request) {

    return transferHandler.create(request)
      .map(response ->
        ResponseEntity
          .created(
            URI.create(
              "/api/transfers/" + response.transferId()
            )
          )
          .body(response)
      );
  }

  @GetMapping("/{id}")
  public Mono<ResponseEntity<TransferResponse>> findById(
    @PathVariable UUID id) {

    return transferHandler.findById(id)
      .map(ResponseEntity::ok);
  }
}