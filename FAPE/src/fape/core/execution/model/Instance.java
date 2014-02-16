/*
 * Author:  Filip Dvořák <filip.dvorak@runbox.com>
 *
 * Copyright (c) 2013 Filip Dvořák <filip.dvorak@runbox.com>, all rights reserved
 *
 * Publishing, providing further or using this program is prohibited
 * without previous written permission of the author. Publishing or providing
 * further the contents of this file is prohibited without previous written
 * permission of the author.
 */

package fape.core.execution.model;

/**
 *
 * @author FD
 */
public class Instance {

    public Instance() {}

    public Instance(String name, String type) {
        this.name = name;
        this.type = type;
    }

    /**
     *
     */
    public String name;

    /**
     *
     */
    public String type;

    @Override
    public String toString() {
        return name+"["+type+"]";
    }

}
