package io.casehub.platform.graphql.generator.infer;

import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import org.jboss.jandex.IndexView;

import javax.lang.model.element.ExecutableElement;

public final class MethodBodyInferrer {

    private MethodBodyInferrer() {}

    public static InferredCallChain infer(Trees trees,
                                           ExecutableElement method,
                                           IndexView jandexIndex) {
        MethodTree methodTree = trees.getTree(method);
        if (methodTree == null || methodTree.getBody() == null) {
            return new InferredCallChain(null, null, null, null, null,
                    null, null, null, null, InferenceFailure.NO_METHOD_BODY);
        }
        TreePath methodPath = trees.getPath(method);
        CallChainScanner scanner = new CallChainScanner(jandexIndex, methodPath);
        scanner.scan(methodPath, null);
        return scanner.result();
    }
}
