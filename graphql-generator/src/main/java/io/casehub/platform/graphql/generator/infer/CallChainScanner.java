package io.casehub.platform.graphql.generator.infer;

import com.sun.source.tree.*;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.IndexView;

import javax.lang.model.element.Name;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class CallChainScanner extends TreePathScanner<Void, Void> {

    private final IndexView jandexIndex;
    private final Map<String, String> importMap;

    private final Map<String, String> variableTypes = new HashMap<>();

    private String spiTypeFqcn;
    private String routingParamName;
    private String capabilityFqcn;
    private String capabilityAccessor;
    private List<String> accessorArgs;
    private String targetMethod;
    private List<String> targetArgs;
    private final Map<String, String> staticDefaults = new LinkedHashMap<>();
    private String requireCapabilityMethodName;

    private int returnCount;
    private InferenceFailure failureReason;

    CallChainScanner(IndexView jandexIndex, TreePath methodPath) {
        this.jandexIndex = jandexIndex;
        this.importMap = buildImportMap(methodPath);
    }

    private static Map<String, String> buildImportMap(TreePath methodPath) {
        Map<String, String> map = new HashMap<>();
        TreePath current = methodPath;
        while (current != null) {
            if (current.getLeaf() instanceof CompilationUnitTree cu) {
                for (ImportTree imp : cu.getImports()) {
                    if (!imp.isStatic()) {
                        String fqcn = imp.getQualifiedIdentifier().toString();
                        if (!fqcn.endsWith(".*")) {
                            String simple = fqcn.substring(fqcn.lastIndexOf('.') + 1);
                            map.put(simple, fqcn);
                        }
                    }
                }
                break;
            }
            current = current.getParentPath();
        }
        return map;
    }

    private String resolveTypeName(String simpleName) {
        if (simpleName.contains(".")) return simpleName;
        String fromImports = importMap.get(simpleName);
        if (fromImports != null) return fromImports;
        if (jandexIndex != null) {
            List<ClassInfo> candidates = new ArrayList<>();
            for (ClassInfo ci : jandexIndex.getKnownClasses()) {
                if (ci.simpleName().equals(simpleName)) {
                    candidates.add(ci);
                }
            }
            if (candidates.size() == 1) return candidates.get(0).name().toString();
        }
        return simpleName;
    }

    // -- Scope guards --

    @Override
    public Void visitLambdaExpression(LambdaExpressionTree node, Void p) {
        return null;
    }

    @Override
    public Void visitNewClass(NewClassTree node, Void p) {
        if (node.getClassBody() != null) {
            return null;
        }
        return super.visitNewClass(node, p);
    }

    // -- Rule 1 & Rule 2: Variable declarations --

    @Override
    public Void visitVariable(VariableTree node, Void p) {
        String typeName = node.getType().toString();

        if ("var".equals(typeName)) {
            if (node.getInitializer() instanceof MethodInvocationTree inv
                    && methodName(inv).equals("platform")) {
                failureReason = InferenceFailure.VAR_TYPED_SPI;
            }
            return super.visitVariable(node, p);
        }

        String resolvedType = resolveTypeName(typeName);
        variableTypes.put(node.getName().toString(), resolvedType);

        if (node.getInitializer() instanceof MethodInvocationTree inv) {
            String mName = methodName(inv);
            if (mName.equals("platform") && inv.getArguments().size() == 1) {
                // Rule 1: service.platform(routingParam)
                spiTypeFqcn = resolvedType;
                routingParamName = inv.getArguments().get(0).toString();
            } else if (mName.equals("platform") && inv.getArguments().isEmpty()) {
                // Rule 2: resolved.platform() — voice-style
                spiTypeFqcn = resolvedType;
            }
        }

        // Rule 5: static default detection — x != null ? x : literal
        if (node.getInitializer() instanceof ConditionalExpressionTree ternary) {
            if (ternary.getFalseExpression() instanceof LiteralTree lit) {
                String paramName = extractParamName(ternary.getCondition(),
                        ternary.getTrueExpression());
                if (paramName != null && lit.getValue() != null) {
                    staticDefaults.put(paramName, lit.getValue().toString());
                }
            }
        }
        return super.visitVariable(node, p);
    }

    private String extractParamName(ExpressionTree condition, ExpressionTree trueExpr) {
        if (!(condition instanceof BinaryTree binary)) return null;
        if (binary.getKind() != Tree.Kind.NOT_EQUAL_TO) return null;
        ExpressionTree left = binary.getLeftOperand();
        ExpressionTree right = binary.getRightOperand();
        boolean leftIsNull = right instanceof LiteralTree lit && lit.getValue() == null;
        boolean rightIsNull = left instanceof LiteralTree lit2 && lit2.getValue() == null;
        if (!leftIsNull && !rightIsNull) return null;
        ExpressionTree paramExpr = leftIsNull ? left : right;
        if (!(paramExpr instanceof IdentifierTree paramId)) return null;
        if (!(trueExpr instanceof IdentifierTree trueId)) return null;
        if (!paramId.getName().contentEquals(trueId.getName())) return null;
        return paramId.getName().toString();
    }

    // -- Rule 3: requireCapability() --
    // -- Rule 6: void method expression statement --

    @Override
    public Void visitExpressionStatement(ExpressionStatementTree node, Void p) {
        if (node.getExpression() instanceof MethodInvocationTree inv) {
            // Rule 3: requireCapability()
            if (methodName(inv).equals("requireCapability")) {
                List<? extends ExpressionTree> args = inv.getArguments();
                if (args.size() >= 2) {
                    ExpressionTree capArg = args.get(1);
                    if (capArg instanceof MemberSelectTree ms
                            && ms.getIdentifier().contentEquals("class")) {
                        capabilityFqcn = resolveTypeName(ms.getExpression().toString());
                    }
                    if (args.size() >= 3 && args.get(2) instanceof LiteralTree lit) {
                        requireCapabilityMethodName = lit.getValue().toString();
                    }
                }
            }

            // Rule 6: void method SPI chain as expression statement
            List<MethodCallInfo> chain = flattenMethodChain(inv);
            if (chain.size() >= 2) {
                MethodCallInfo first = chain.get(0);
                if (first.receiverName() != null
                        && variableTypes.containsKey(first.receiverName())
                        && variableTypes.get(first.receiverName()).equals(spiTypeFqcn)) {
                    if (targetMethod == null) {
                        capabilityAccessor = first.methodName();
                        accessorArgs = first.args();
                        MethodCallInfo last = chain.get(chain.size() - 1);
                        targetMethod = last.methodName();
                        targetArgs = last.args();
                    }
                }
            } else if (chain.size() == 1) {
                MethodCallInfo call = chain.get(0);
                if (call.receiverName() != null
                        && variableTypes.containsKey(call.receiverName())
                        && variableTypes.get(call.receiverName()).equals(spiTypeFqcn)
                        && targetMethod == null) {
                    targetMethod = call.methodName();
                    targetArgs = call.args();
                }
            }
        }
        return super.visitExpressionStatement(node, p);
    }

    // -- Rule 4: Return statement with method chain --

    @Override
    public Void visitReturn(ReturnTree node, Void p) {
        returnCount++;
        if (returnCount > 1) {
            spiTypeFqcn = null;
            capabilityAccessor = null;
            targetMethod = null;
            targetArgs = null;
            failureReason = InferenceFailure.MULTIPLE_RETURNS;
            return super.visitReturn(node, p);
        }

        ExpressionTree expr = node.getExpression();
        if (expr == null) return super.visitReturn(node, p);

        List<MethodCallInfo> chain = flattenMethodChain(expr);

        if (chain.size() >= 2) {
            MethodCallInfo first = chain.get(0);
            if (first.receiverName() != null
                    && variableTypes.containsKey(first.receiverName())
                    && variableTypes.get(first.receiverName()).equals(spiTypeFqcn)) {
                capabilityAccessor = first.methodName();
                accessorArgs = first.args();
                MethodCallInfo last = chain.get(chain.size() - 1);
                targetMethod = last.methodName();
                targetArgs = last.args();
            }
        } else if (chain.size() == 1) {
            MethodCallInfo call = chain.get(0);
            if (call.receiverName() != null
                    && variableTypes.containsKey(call.receiverName())
                    && variableTypes.get(call.receiverName()).equals(spiTypeFqcn)) {
                targetMethod = call.methodName();
                targetArgs = call.args();
            }
        }
        return super.visitReturn(node, p);
    }

    // -- Method chain flattening --

    private List<MethodCallInfo> flattenMethodChain(ExpressionTree expr) {
        List<MethodCallInfo> chain = new ArrayList<>();
        flattenMethodChainRecursive(expr, chain);
        return chain;
    }

    private void flattenMethodChainRecursive(ExpressionTree expr, List<MethodCallInfo> chain) {
        if (!(expr instanceof MethodInvocationTree inv)) return;

        ExpressionTree methodSelect = inv.getMethodSelect();
        List<String> args = inv.getArguments().stream()
                .map(ExpressionTree::toString)
                .toList();

        if (methodSelect instanceof MemberSelectTree ms) {
            flattenMethodChainRecursive(ms.getExpression(), chain);
            String receiverName = (chain.isEmpty() && ms.getExpression() instanceof IdentifierTree id)
                    ? id.getName().toString() : null;
            chain.add(new MethodCallInfo(receiverName, ms.getIdentifier().toString(), args));
        }
    }

    // -- Utilities --

    private static String methodName(MethodInvocationTree inv) {
        ExpressionTree ms = inv.getMethodSelect();
        if (ms instanceof MemberSelectTree mst) {
            return mst.getIdentifier().toString();
        }
        if (ms instanceof IdentifierTree id) {
            return id.getName().toString();
        }
        return "";
    }

    // -- Result assembly --

    InferredCallChain result() {
        if (requireCapabilityMethodName != null && targetMethod != null
                && !requireCapabilityMethodName.equals(targetMethod)) {
            failureReason = InferenceFailure.CAPABILITY_MISMATCH;
        }
        if (spiTypeFqcn == null && failureReason == null) {
            failureReason = InferenceFailure.NO_SPI_VARIABLE;
        }
        if (capabilityFqcn != null && capabilityAccessor == null && failureReason == null) {
            failureReason = InferenceFailure.INCOMPLETE_CAPABILITY_CHAIN;
        }
        if (spiTypeFqcn != null && targetMethod == null && failureReason == null) {
            failureReason = InferenceFailure.UNRECOGNIZED_PATTERN;
        }
        return new InferredCallChain(spiTypeFqcn, routingParamName,
                capabilityFqcn, capabilityAccessor, accessorArgs,
                targetMethod, targetArgs, staticDefaults.isEmpty() ? Map.of() : Map.copyOf(staticDefaults),
                requireCapabilityMethodName, failureReason);
    }
}
