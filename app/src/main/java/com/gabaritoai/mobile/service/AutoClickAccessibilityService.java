package com.gabaritoai.mobile.service;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

public class AutoClickAccessibilityService extends AccessibilityService {
    private static final String TAG = "AutoClickService";
    private static AutoClickAccessibilityService instance;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "GabaritoAI Accessibility Service Conectado!");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Can be used to monitor app switches or window changes
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "GabaritoAI Accessibility Service Interrompido.");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    public static boolean isRunning() {
        return instance != null;
    }

    /**
     * Tries to find and automatically click the option with the specified letter (A, B, C, D, E)
     */
    public static boolean clickOption(String targetLetter) {
        if (instance == null || targetLetter == null || targetLetter.isEmpty()) {
            return false;
        }

        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        if (root == null) return false;

        String letter = targetLetter.toUpperCase().trim();
        try {
            return instance.findAndClickNode(root, letter);
        } finally {
            root.recycle();
        }
    }

    private boolean findAndClickNode(AccessibilityNodeInfo rootNode, String targetLetter) {
        if (rootNode == null) return false;

        // Try exact letter match or prefix: "A)", "A.", "A -", "(A)"
        String prefix1 = targetLetter + ")";
        String prefix2 = targetLetter + ".";
        String prefix3 = "(" + targetLetter + ")";

        // Search for matching text
        List<AccessibilityNodeInfo> matchingNodes = rootNode.findAccessibilityNodeInfosByText(targetLetter);
        if (matchingNodes != null && !matchingNodes.isEmpty()) {
            for (AccessibilityNodeInfo node : matchingNodes) {
                CharSequence text = node.getText();
                CharSequence desc = node.getContentDescription();
                String content = text != null ? text.toString() : (desc != null ? desc.toString() : "");
                content = content.trim();

                boolean isTarget = content.equalsIgnoreCase(targetLetter) ||
                        content.startsWith(prefix1) ||
                        content.startsWith(prefix2) ||
                        content.startsWith(prefix3);

                if (isTarget) {
                    // Try to click the node or its clickable parent
                    AccessibilityNodeInfo clickable = findClickableAncestor(node);
                    if (clickable != null) {
                        boolean clicked = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        clickable.recycle();
                        if (clicked) {
                            Log.i(TAG, "Sucesso ao auto-clicar na alternativa " + targetLetter);
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private AccessibilityNodeInfo findClickableAncestor(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        while (current != null) {
            if (current.isClickable()) {
                return current;
            }
            AccessibilityNodeInfo parent = current.getParent();
            if (current != node) {
                current.recycle();
            }
            current = parent;
        }
        return null;
    }
}
