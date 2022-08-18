package io.yapix.parse.parser;

import static io.yapix.parse.constant.WsConstants.OpRequestDeleteMapping;
import static io.yapix.parse.constant.WsConstants.OpRequestSaveMapping;
import static io.yapix.parse.constant.WsConstants.OpRequestSelectMapping;
import static io.yapix.parse.constant.WsConstants.OpRequestUpdateMapping;
import static io.yapix.parse.constant.WsConstants.OpRequestUploadMapping;

import com.google.common.collect.Lists;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiMethod;
import io.yapix.base.util.JsonUtils;
import io.yapix.base.util.NotificationUtils;
import io.yapix.model.HttpMethod;
import io.yapix.parse.constant.SpringConstants;
import io.yapix.parse.constant.WxbConstants;
import io.yapix.parse.model.PathParseInfo;
import io.yapix.parse.util.PathUtils;
import io.yapix.parse.util.PsiAnnotationUtils;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import io.yapix.parse.util.WsUtils;
import org.apache.commons.collections.CollectionUtils;

/**
 * 路径请求相关工具解析类
 */
public class PathParser {

    private static final Map<HttpMethod, String> simpleMappings = new LinkedHashMap<>();

    private static final Set<String> wsMappings = new LinkedHashSet<>();

    static {
        simpleMappings.put(HttpMethod.GET, SpringConstants.GetMapping);
        simpleMappings.put(HttpMethod.POST, SpringConstants.PostMapping);
        simpleMappings.put(HttpMethod.PUT, SpringConstants.PutMapping);
        simpleMappings.put(HttpMethod.DELETE, SpringConstants.DeleteMapping);
        simpleMappings.put(HttpMethod.PATCH, SpringConstants.PatchMapping);
        //ws
        wsMappings.add(OpRequestSelectMapping);
        wsMappings.add(OpRequestSaveMapping);
        wsMappings.add(OpRequestUpdateMapping);
        wsMappings.add(OpRequestDeleteMapping);
        wsMappings.add(OpRequestUploadMapping);
    }

    /**
     * 解析请求映射信息
     */
    public static PathParseInfo parse(PsiMethod method) {
        PathParseInfo pathInfo = null;
        PsiAnnotation requestMapping = PsiAnnotationUtils.getAnnotation(method, SpringConstants.RequestMapping);
        if (requestMapping != null && !WsUtils.isWsMethod(method)) {
            pathInfo = parseRequestMappingAnnotation(requestMapping);
        } else {
            for (Entry<HttpMethod, String> item : simpleMappings.entrySet()) {
                PsiAnnotation annotation = PsiAnnotationUtils.getAnnotation(method, item.getValue());
                if (annotation != null) {
                    pathInfo = parseSimpleMappingAnnotation(item.getKey(), annotation);
                    break;
                }
            }
            for (String item : wsMappings) {
                PsiAnnotation annotation = PsiAnnotationUtils.getAnnotation(method, item);
                if (annotation != null) {
                    pathInfo = parseWsMappingAnnotation(item);
                    break;
                }
            }
        }

        // 公司内部定制@ApiVersion注解
        if (pathInfo != null && CollectionUtils.isNotEmpty(pathInfo.getPaths())) {
            wxbPathHandle(method, pathInfo);
        }
        return pathInfo;
    }

    /**
     * 小宝定制
     */
    private static void wxbPathHandle(PsiMethod method, PathParseInfo pathInfo) {
        PsiAnnotation apiVersion = PsiAnnotationUtils.getAnnotation(method, WxbConstants.ApiVersion);
        if (apiVersion == null && method.getContainingClass() != null) {
            apiVersion = PsiAnnotationUtils.getAnnotation(method.getContainingClass(), WxbConstants.ApiVersion);
        }
        if (apiVersion == null) {
            return;
        }
        Long version = AnnotationUtil.getLongAttributeValue(apiVersion, PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
        if (version == null) {
            return;
        }

        List<String> paths = Lists.newArrayListWithCapacity(pathInfo.getPaths().size());
        for (String p : pathInfo.getPaths()) {
            String path = p.replaceAll("\\{\\s*version\\s*}", "v" + version);
            paths.add(path);
        }
        pathInfo.setPaths(paths);
    }

    /**
     * 解析@RequestMapping的信息
     */
    public static PathParseInfo parseRequestMappingAnnotation(PsiAnnotation annotation) {
        List<String> paths = getPaths(annotation);
        List<HttpMethod> methods = PsiAnnotationUtils.getStringArrayAttribute(annotation, "method").stream()
                .map(HttpMethod::of).collect(Collectors.toList());
        if (methods.isEmpty()) {
            // 未指定方法，那么默认
            methods.add(HttpMethod.GET);
        }
        PathParseInfo mapping = new PathParseInfo();
        mapping.setMethod(methods.get(0));
        mapping.setPaths(paths);
        return mapping;
    }

    /**
     * 解析其他注解信息，例如: @GetMapping, @PostMapping, ....
     */
    public static PathParseInfo parseSimpleMappingAnnotation(HttpMethod method, PsiAnnotation annotation) {
        List<String> paths = getPaths(annotation);
        PathParseInfo info = new PathParseInfo();
        info.setPaths(paths);
        info.setMethod(method);
        return info;
    }

    public static PathParseInfo parseWsMappingAnnotation(String item) {
        List<String> paths = getWsPaths(item);
        PathParseInfo info = new PathParseInfo();
        info.setPaths(paths);
        info.setMethod(HttpMethod.POST);
        return info;
    }

    private static List<String> getPaths(PsiAnnotation annotation) {
        List<String> paths = PsiAnnotationUtils.getStringArrayAttribute(annotation, "path");
        if (paths.isEmpty()) {
            paths = PsiAnnotationUtils.getStringArrayAttribute(annotation, "value");
        }
        if (paths.isEmpty()) {
            paths.add("");
        }
        // 清除路径变量正则部分
        paths = paths.stream().map(PathUtils::clearPathPattern).collect(Collectors.toList());
        return paths;
    }

    private static List<String> getWsPaths(String item) {
        List<String> paths;
        switch (item) {
            case OpRequestSelectMapping:
                paths = Lists.newArrayList("select" + UUID.randomUUID().toString().substring(0, 5));
                break;
            case OpRequestDeleteMapping:
                paths = Lists.newArrayList("delete" + UUID.randomUUID().toString().substring(0, 5));
                break;
            case OpRequestSaveMapping:
                paths = Lists.newArrayList("save" + UUID.randomUUID().toString().substring(0, 5));
                break;
            case OpRequestUpdateMapping:
                paths = Lists.newArrayList("update" + UUID.randomUUID().toString().substring(0, 5));
                break;
            case OpRequestUploadMapping:
                paths = Lists.newArrayList("upload" + UUID.randomUUID().toString().substring(0, 5));
                break;
            default:
                paths = Lists.newArrayList();
                break;
        }
        // 清除路径变量正则部分
        paths = paths.stream().map(PathUtils::clearPathPattern).collect(Collectors.toList());
        return paths;
    }
}
