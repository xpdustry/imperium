// mdt loads these helpers at server startup. The JS scope persists between console commands.

/** Reads a field by name, walking up the class hierarchy and ignoring visibility. */
function field(obj, name) {
    return findField(obj.getClass(), name).get(obj);
}

/** Writes a field by name, walking up the class hierarchy and ignoring visibility. */
function setField(obj, name, value) {
    let f = findField(obj.getClass(), name);
    // Use typed setters to write primitive fields from JS numbers.
    let setter = {
        "int": "setInt", "long": "setLong", "float": "setFloat", "double": "setDouble",
        "short": "setShort", "byte": "setByte", "char": "setChar", "boolean": "setBoolean"
    }[f.getType().getName()];
    if (setter) f[setter](obj, value);
    else f.set(obj, value);
}

function findField(type, name) {
    for (let c = type; c != null; c = c.getSuperclass()) {
        try {
            let f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (e) {}
    }
    throw new Error("no field " + name + " in " + type.getName());
}

/** Calls a method by name and argument count, ignoring visibility: call(obj, "name", arg1, arg2). */
function call(obj, name) {
    let args = Array.prototype.slice.call(arguments, 2);
    for (let c = obj.getClass(); c != null; c = c.getSuperclass()) {
        let methods = c.getDeclaredMethods();
        for (let i = 0; i < methods.length; i++) {
            if (methods[i].getName() == name && methods[i].getParameterCount() == args.length) {
                methods[i].setAccessible(true);
                // Rhino's method dispatch converts JS arguments to the parameter types.
                return Function.prototype.apply.call(new Packages.rhino.NativeJavaMethod(methods[i], name), obj, args);
            }
        }
    }
    throw new Error("no method " + name + " with " + args.length + " args in " + obj.getClass().getName());
}

/** Lists the declared fields of an object with their values, one per line. */
function fields(obj) {
    let out = [];
    for (let c = obj.getClass(); c != null && c != java.lang.Object; c = c.getSuperclass()) {
        let fs = c.getDeclaredFields();
        for (let i = 0; i < fs.length; i++) {
            if (java.lang.reflect.Modifier.isStatic(fs[i].getModifiers())) continue;
            try {
                fs[i].setAccessible(true);
                out.push(fs[i].getName() + " = " + fs[i].get(obj));
            } catch (e) {
                out.push(fs[i].getName() + " = <" + e + ">");
            }
        }
    }
    return out.join("\n");
}

/** Loads a class from any loaded mod or plugin, e.g. cls("com.example.MyPlugin"). */
function cls(name) {
    return java.lang.Class.forName(name, true, Vars.mods.mainLoader());
}

/** The instance of a Kotlin object declaration. */
function kobject(name) {
    return cls(name).getField("INSTANCE").get(null);
}

/** The main class instance of a mod or plugin, by its plugin.json name. */
function mod(name) {
    let m = Vars.mods.getMod(name);
    if (m == null) throw new Error("no mod named " + name + ", loaded: " + mods());
    return m.main;
}

/** Loaded mods and plugins with their version and state. */
function mods() {
    let out = [];
    Vars.mods.list().each(function (m) {
        out.push(m.name + " " + m.meta.version + " " + m.state);
    });
    return out.join(", ");
}

/** Online players as "name uuid". */
function players() {
    let out = [];
    Groups.player.each(function (p) {
        out.push(p.plainName() + " " + p.uuid());
    });
    return out.length == 0 ? "no players" : out.join("\n");
}

"generic prelude loaded, mods: " + mods();
