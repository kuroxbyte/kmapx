package dev.kmapx.intellij

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Iconos propios del plugin (16×16, con variante `_dark` que el IDE elige solo):
 *  - [ToGenerated]: clase source → su código generado (caja origen + flecha saliente).
 *  - [FromGenerated]: target ← quién lo produce (flecha entrante + caja destino llena).
 */
object KmapxIcons {
    @JvmField val ToGenerated: Icon = IconLoader.getIcon("/icons/toGenerated.svg", KmapxIcons::class.java)
    @JvmField val FromGenerated: Icon = IconLoader.getIcon("/icons/fromGenerated.svg", KmapxIcons::class.java)
}
