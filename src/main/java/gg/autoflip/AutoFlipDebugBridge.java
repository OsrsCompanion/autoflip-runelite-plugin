package gg.autoflip;

import java.awt.Component;
import java.lang.reflect.Method;
import javax.swing.JButton;

final class AutoFlipDebugBridge
{
    private AutoFlipDebugBridge()
    {
    }

    static JButton createButton(AutoFlipPlugin plugin, Component parentComponent)
    {
        try
        {
            Class<?> debugClass = Class.forName("gg.autoflip.AutoFlipLocalDebugPanel");
            Method createButton = debugClass.getDeclaredMethod(
                "createButton",
                AutoFlipPlugin.class,
                Component.class
            );
            createButton.setAccessible(true);
            Object result = createButton.invoke(null, plugin, parentComponent);
            return result instanceof JButton ? (JButton) result : null;
        }
        catch (ClassNotFoundException ignored)
        {
            return null;
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }
}
