package io.quarkus.oidc.token.propagation.common.deployment;

import static java.util.stream.Collectors.groupingBy;

import java.util.List;
import java.util.Objects;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.oidc.token.propagation.common.AccessToken;

public class OidcTokenPropagationCommonProcessor {

    private static final DotName ACCESS_TOKEN = DotName.createSimple(AccessToken.class.getName());

    @BuildStep
    public List<AccessTokenInstanceBuildItem> collectAccessTokenInstances(CombinedIndexBuildItem index) {
        record ItemBuilder(AnnotationInstance instance) {

            private String toClientName() {
                var value = instance.value("exchangeTokenClient");
                return value == null || value.asString().equals("Default") ? "" : value.asString();
            }

            private boolean toExchangeToken() {
                return instance.value("exchangeTokenClient") != null;
            }

            private MethodInfo methodInfo() {
                if (instance.target().kind() == AnnotationTarget.Kind.METHOD) {
                    return instance.target().asMethod();
                }
                return null;
            }

            private String targetClassName() {
                if (instance.target().kind() == AnnotationTarget.Kind.METHOD) {
                    return instance.target().asMethod().declaringClass().name().toString();
                }
                return instance.target().asClass().name().toString();
            }

            private AccessTokenInstanceBuildItem build() {
                return new AccessTokenInstanceBuildItem(toClientName(), toExchangeToken(), instance.target(), methodInfo());
            }
        }
        var accessTokenAnnotations = index.getIndex().getAnnotations(ACCESS_TOKEN);
        var itemBuilders = accessTokenAnnotations.stream().map(ItemBuilder::new).toList();
        if (!itemBuilders.isEmpty()) {
            var targetClassToBuilders = itemBuilders.stream().collect(groupingBy(ItemBuilder::targetClassName));
            targetClassToBuilders.forEach((targetClassName, classBuilders) -> {
                if (classBuilders.size() > 1 && classBuilders.stream().map(ItemBuilder::methodInfo).anyMatch(Objects::isNull)) {
                    throw new RuntimeException(
                            ACCESS_TOKEN + " annotation can be applied either on class " + targetClassName + " or its methods");
                }
            });
        }
        return itemBuilders.stream().map(ItemBuilder::build).toList();
    }

}
