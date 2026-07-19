package dev.kmapx.core.engine

/**
 * KMX008— detección de ciclos sobre el grafo de mapeos declarados/referenciados.
 * Datos puros: los nodos son qualified names; las aristas, "el mapeo de A referencia el de B"
 * (un `@MapTo` anidado). El frontend arma las aristas; el core solo razona.
 */
public object MappingGraph {

    /**
     * Primer ciclo encontrado, como camino completo que empieza y termina en el mismo nodo
     * (`[Person, Address, Person]`), o null si el grafo es acíclico. Determinista: DFS en el
     * orden de declaración.
     */
    public fun findCycle(edges: Map<String, List<String>>): List<String>? {
        val visited = mutableSetOf<String>()
        val inPath = mutableSetOf<String>()
        val path = mutableListOf<String>()

        fun dfs(node: String): List<String>? {
            if (node in inPath) return path.subList(path.indexOf(node), path.size) + node
            if (node in visited) return null
            visited += node
            inPath += node
            path += node
            for (next in edges[node].orEmpty()) {
                dfs(next)?.let { return it }
            }
            inPath -= node
            path.removeAt(path.lastIndex)
            return null
        }

        for (start in edges.keys) {
            dfs(start)?.let { return it }
        }
        return null
    }
}
