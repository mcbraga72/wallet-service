package com.recargapay.wallet_service.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recargapay.wallet_service.domain.IdempotencyKey;
import com.recargapay.wallet_service.dto.response.ApiResponse;
import com.recargapay.wallet_service.enums.IdempotencyKeyStatus;
import com.recargapay.wallet_service.repository.IdempotencyKeyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class IdempotencyKeyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyKeyService(IdempotencyKeyRepository idempotencyKeyRepository, ObjectMapper objectMapper) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.objectMapper = objectMapper;
    }

    public String serialize(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            throw new RuntimeException("Serialization error", e);
        }
    }

    public <T> T deserialize(String json, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (Exception e) {
            throw new RuntimeException("Deserialization error", e);
        }
    }

    public <T> ApiResponse<T> saveOrReplay(
            UUID idempotencyKey,
            UUID userId,
            ApiResponse<T> response,
            TypeReference<ApiResponse<T>> typeReference
    ) {
        try {
            idempotencyKeyRepository.save(new IdempotencyKey(
                    idempotencyKey,
                    userId,
                    IdempotencyKeyStatus.SUCCESS,
                    serialize(response)
            ));

            return response;
        } catch (DataIntegrityViolationException ex) {
            log.warn("Idempotency conflict detected for key={} userId={}", idempotencyKey, userId);

            IdempotencyKey existing = idempotencyKeyRepository
                    .findByIdempotencyKeyAndUserPublicId(idempotencyKey, userId)
                    .orElseThrow();

            return deserialize(existing.getResponse(), typeReference);
        }
    }
}
