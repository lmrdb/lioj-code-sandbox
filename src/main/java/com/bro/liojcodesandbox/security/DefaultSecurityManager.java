package com.bro.liojcodesandbox.security;

import java.security.Permission;
import java.util.PropertyPermission;

/**
 * 默认安全管理器
 */
public class DefaultSecurityManager extends SecurityManager{

    //检查所有的权限
    @Override
    public void checkPermission(Permission perm) {
        System.out.println("默认不做任何限制");

        //super.checkPermission(perm);
    }


}
