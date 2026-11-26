package id.imzyy.xl

import java.net.URLEncoder
import org.json.JSONArray

data class Hit(val id: String, val slug: String, val title: String, val desc: String, val downloads: Long)

data class ModFile(
    val projectId: String,
    val slug: String,
    val filename: String,
    val url: String,
    val sha512: String?,
    val sha1: String?,
    val requiredProjects: List<String>,
    val size: Long = 0L,
)

object Modrinth {
    private const val API = "https://api.modrinth.com/v2"
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Release versions of Minecraft, newest first. */
    fun releaseVersions(): List<String> {
        val arr = JSONArray(Net.getText("$API/tag/game_version"))
        val rows = ArrayList<Pair<String, String>>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("version_type") == "release") rows.add(o.optString("date") to o.getString("version"))
        }
        return rows.sortedByDescending { it.first }.map { it.second }
    }

    fun search(query: String, gameVersion: String, loader: String): List<Hit> {
        val facets = JSONArray()
            .put(JSONArray().put("project_type:mod"))
            .put(JSONArray().put("versions:$gameVersion"))
            .put(JSONArray().put("categories:$loader"))
            .put(JSONArray().put("client_side:required").put("client_side:optional"))
        val url = "$API/search?query=${enc(query)}&limit=20&index=relevance&facets=${enc(facets.toString())}"
        val hits = org.json.JSONObject(Net.getText(url)).getJSONArray("hits")
        val out = ArrayList<Hit>()
        for (i in 0 until hits.length()) {
            val h = hits.getJSONObject(i)
            out.add(Hit(h.getString("project_id"), h.optString("slug"), h.optString("title"), h.optString("description"), h.optLong("downloads")))
        }
        return out
    }

    /** Newest compatible file (prefers release builds); null if the project has no matching build. */
    fun resolve(idOrSlug: String, gameVersion: String, loader: String): ModFile? {
        val url = "$API/project/${enc(idOrSlug)}/version?loaders=${enc("[\"$loader\"]")}&game_versions=${enc("[\"$gameVersion\"]")}"
        val arr = try {
            JSONArray(Net.getText(url))
        } catch (e: HttpFailure) {
            if (e.code == 404) return null else throw e
        }
        if (arr.length() == 0) return null
        var pick = arr.getJSONObject(0)
        for (i in 0 until arr.length()) {
            val v = arr.getJSONObject(i)
            if (v.optString("version_type") == "release") { pick = v; break }
        }
        val files = pick.getJSONArray("files")
        var file = files.getJSONObject(0)
        for (i in 0 until files.length()) {
            val f = files.getJSONObject(i)
            if (f.optBoolean("primary")) { file = f; break }
        }
        val name = file.getString("filename")
        if (!name.endsWith(".jar")) return null
        val hashes = file.optJSONObject("hashes")
        val deps = ArrayList<String>()
        val da = pick.optJSONArray("dependencies")
        if (da != null) for (i in 0 until da.length()) {
            val d = da.getJSONObject(i)
            if (d.optString("dependency_type") == "required" && !d.isNull("project_id")) deps.add(d.getString("project_id"))
        }
        return ModFile(
            projectId = pick.getString("project_id"),
            slug = idOrSlug,
            filename = name,
            url = file.getString("url"),
            sha512 = hashes?.optString("sha512")?.ifEmpty { null },
            sha1 = hashes?.optString("sha1")?.ifEmpty { null },
            requiredProjects = deps,
            size = file.optLong("size"),
        )
    }
}
