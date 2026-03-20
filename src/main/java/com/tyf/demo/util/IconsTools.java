package com.tyf.demo.util;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import com.formdev.flatlaf.extras.FlatSVGIcon;

public class IconsTools {

    /**
     *   @desc : 获取图标
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public static Icon getIcon(String resourceName, int width, int height) {
        if (resourceName.endsWith(".svg")) {
            return new FlatSVGIcon(resourceName.substring(1), width, height);
        }
        else {
            return new ImageIcon(IconsTools.class.getResource(resourceName));
        }
    }

    /**
     *   @desc : 获取图标
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    /**
     *   @desc : 获取图标
     *   @auth : tyf
     *   @date : 2026-03-20 14:04:14
    */
    public static Icon getIcon(String resourceName) {
        return getIcon(resourceName, 24, 24);
    }

    /** app 图标 */
    public static Icon app = getIcon("/icons/app.svg");



}
