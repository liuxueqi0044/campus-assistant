package cn.iocoder.yudao.module.campus.identity.api;

public interface OperatorAuthorizationApi {

    AuthorizedSchoolScope authorize(long operatorId, OperatorAction action,
                                    RequestedSchoolScope requestedScope);
}
