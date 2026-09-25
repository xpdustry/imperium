// mdt loads these Imperium helpers after the generic prelude.

var imperium = field(mod("imperium"), "application");

/** An Imperium instance by simple class name, from the dependency container or the listeners. */
function inst(name) {
    let sources = [imperium.getInstances().getAll(), imperium.getListeners()];
    for (let i = 0; i < sources.length; i++) {
        let it = sources[i].iterator();
        while (it.hasNext()) {
            let o = it.next();
            if (o.getClass().getSimpleName() == name) return o;
        }
    }
    throw new Error("no instance named " + name);
}

/** Runs SQL on the Exposed database. Exposed quotes reserved column names, e.g. "name", "start". */
function sql(query) {
    let connection = field(inst("SimpleSQLProvider"), "source").getConnection();
    try {
        let statement = connection.createStatement();
        if (!statement.execute(query)) return "updated " + statement.getUpdateCount();
        let rows = statement.getResultSet(), meta = rows.getMetaData(), out = [];
        while (rows.next()) {
            let row = [];
            for (let i = 1; i <= meta.getColumnCount(); i++) row.push(String(rows.getString(i)));
            out.push(row.join(" | "));
        }
        return out.join("\n");
    } finally {
        connection.close();
    }
}

"imperium prelude loaded";
