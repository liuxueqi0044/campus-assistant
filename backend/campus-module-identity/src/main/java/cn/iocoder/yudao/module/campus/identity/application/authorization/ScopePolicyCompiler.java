package cn.iocoder.yudao.module.campus.identity.application.authorization;

import cn.iocoder.yudao.module.campus.identity.api.AuthorizedSchoolScope;
import cn.iocoder.yudao.module.campus.identity.api.OperatorAction;
import cn.iocoder.yudao.module.campus.identity.api.OperatorAuthorizationApi;
import cn.iocoder.yudao.module.campus.identity.api.RequestedSchoolScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Preflights every requested scope before a batch handler can perform its first business write. */
@Component
@RequiredArgsConstructor
public class ScopePolicyCompiler {

    private final OperatorAuthorizationApi authorizationApi;

    public List<AuthorizedSchoolScope> authorizeAll(long operatorId, OperatorAction action,
                                                    List<RequestedSchoolScope> requestedScopes) {
        Objects.requireNonNull(action, "action");
        List<RequestedSchoolScope> scopes = List.copyOf(
                Objects.requireNonNull(requestedScopes, "requestedScopes"));
        if (scopes.isEmpty()) {
            throw new IllegalArgumentException("batch authorization requires at least one scope");
        }
        List<AuthorizedSchoolScope> authorized = scopes.stream()
                .map(scope -> {
                    if (scope == null) {
                        throw new OperatorAuthorizationException(OperatorAuthorizationFailure.SCOPE_DENIED);
                    }
                    AuthorizedSchoolScope proof = authorizationApi.authorize(operatorId, action, scope);
                    if (proof == null) {
                        throw new OperatorAuthorizationException(OperatorAuthorizationFailure.SCOPE_DENIED);
                    }
                    if (proof.action() != action) {
                        throw new OperatorAuthorizationException(OperatorAuthorizationFailure.ACTION_DENIED);
                    }
                    if (proof.schoolId() != scope.schoolId()
                            || !Objects.equals(proof.campusId(), scope.campusId())) {
                        throw new OperatorAuthorizationException(OperatorAuthorizationFailure.SCOPE_DENIED);
                    }
                    if (!isKnownScopeType(proof)) {
                        throw new OperatorAuthorizationException(OperatorAuthorizationFailure.SCOPE_DENIED);
                    }
                    return proof;
                }).toList();
        long schoolId = authorized.get(0).schoolId();
        if (authorized.stream().anyMatch(proof -> proof.schoolId() != schoolId)) {
            throw new OperatorAuthorizationException(OperatorAuthorizationFailure.SCOPE_DENIED);
        }
        return authorized;
    }

    private static boolean isKnownScopeType(AuthorizedSchoolScope proof) {
        return switch (proof.grantScopeType()) {
            case "PLATFORM", "SCHOOL" -> true;
            case "CAMPUS" -> proof.campusId() != null && proof.campusId() > 0;
            default -> false;
        };
    }

    public <T> T executeAuthorizedBatch(long operatorId, OperatorAction action,
                                        List<RequestedSchoolScope> requestedScopes,
                                        Function<List<AuthorizedSchoolScope>, T> handler) {
        Objects.requireNonNull(handler, "handler");
        List<AuthorizedSchoolScope> authorized = authorizeAll(operatorId, action, requestedScopes);
        return handler.apply(authorized);
    }
}
