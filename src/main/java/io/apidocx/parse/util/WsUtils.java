package io.yapix.parse.util;

import static io.yapix.parse.constant.WsConstants.OpRequestDeleteMapping;
import static io.yapix.parse.constant.WsConstants.OpRequestSaveMapping;
import static io.yapix.parse.constant.WsConstants.OpRequestSelectMapping;
import static io.yapix.parse.constant.WsConstants.OpRequestUpdateMapping;
import static io.yapix.parse.constant.WsConstants.OpRequestUploadMapping;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import io.yapix.model.Property;
import io.yapix.parse.constant.WsConstants;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class WsUtils {

    private static final Set<String> wsMappings = new LinkedHashSet<>();


    public WsUtils() {

    }

    static {
        //ws
        wsMappings.add(OpRequestSelectMapping);
        wsMappings.add(OpRequestSaveMapping);
        wsMappings.add(OpRequestUpdateMapping);
        wsMappings.add(OpRequestDeleteMapping);
        wsMappings.add(OpRequestUploadMapping);
    }

    public static String getApiSummary(PsiMethod psiMethod) {
        for (String item : wsMappings) {
            PsiAnnotation annotation = PsiAnnotationUtils.getAnnotation(psiMethod, item);
            if (annotation != null) {
                return PsiAnnotationUtils.getWsDescByAnnotation(annotation);
            }
        }
        return null;
    }

    public static boolean isWsMethod(PsiMethod psiMethod) {
        PsiClass controller = psiMethod.getContainingClass();
        assert controller != null;
        PsiAnnotation wsRestController = PsiAnnotationUtils.getAnnotationIncludeExtends(controller,
                WsConstants.WSRestController);
        return wsRestController != null;
    }

    public static boolean isWsController(PsiClass controller) {
        PsiAnnotation wsRestController = PsiAnnotationUtils.getAnnotationIncludeExtends(controller,
                WsConstants.WSRestController);
        return wsRestController != null;
    }

    public static Property getWsProperty(PsiMethod method) {
        Property wsJsonBodyItem = new Property();
        Property op = new Property();
        op.setName("op");
        op.setType("string");
        op.setRequired(true);
        op.setDeprecated(false);
        for (String item : wsMappings) {
            PsiAnnotation annotation = PsiAnnotationUtils.getAnnotation(method, item);
            if (annotation != null) {
                op.setDescription(PsiAnnotationUtils.getWsOpByAnnotation(annotation));
            }
        }
        Property data = new Property();
        data.setName("data");
        data.setType("object");
        data.setRequired(true);
        data.setDeprecated(false);
        Property token = new Property();
        token.setName("token");
        token.setType("string");
        token.setRequired(true);
        token.setDeprecated(false);
        Map<String, Property> properties = new HashMap<>();
        properties.put("op", op);
        properties.put("data", data);
        properties.put("token", token);
        wsJsonBodyItem.setProperties(properties);
        return wsJsonBodyItem;
    }
}
