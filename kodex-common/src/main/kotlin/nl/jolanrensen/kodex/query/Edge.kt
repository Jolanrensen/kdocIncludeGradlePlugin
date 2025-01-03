package nl.jolanrensen.kodex.query

import org.jgrapht.graph.DefaultEdge

data class Edge<T : Any>(val from: T, val to: T) : DefaultEdge() {
    override fun getSource(): Any = from

    override fun getTarget(): Any = to
}
