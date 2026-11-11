package io.casehub.platform.graphql.generator.infer;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class CompileTestHelper {

    final Trees trees;
    private final List<TypeElement> types = new ArrayList<>();

    CompileTestHelper(String... sourceFiles) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null);
        List<File> sources = new ArrayList<>();
        for (String sf : sourceFiles) {
            sources.add(new File("src/test/resources/infer/" + sf));
        }
        Iterable<? extends JavaFileObject> units = fm.getJavaFileObjects(
                sources.toArray(new File[0]));
        JavacTask task = (JavacTask) compiler.getTask(null, fm, null,
                List.of("-proc:none"), null, units);
        Iterable<? extends Element> elements = task.analyze();
        trees = Trees.instance(task);
        for (Element e : elements) {
            if (e instanceof TypeElement te) {
                types.add(te);
            }
        }
    }

    TypeElement findType(String simpleName) {
        for (TypeElement te : types) {
            if (te.getSimpleName().contentEquals(simpleName)) {
                return te;
            }
        }
        throw new AssertionError("Type not found: " + simpleName);
    }

    ExecutableElement findMethod(String typeName, String methodName) {
        TypeElement type = findType(typeName);
        for (Element e : type.getEnclosedElements()) {
            if (e instanceof ExecutableElement exec
                    && exec.getSimpleName().contentEquals(methodName)) {
                return exec;
            }
        }
        throw new AssertionError("Method not found: " + typeName + "." + methodName);
    }
}
