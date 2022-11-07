package cn.pantheon

import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap


data class HostRecord(
    val sourceHost: String,
    val sourcePort: Int,
    val targetHost: String,
    val targetPort: Int
)


interface HostProvider {

    fun init() {}

    fun provide(): List<HostRecord>

}


class HostPlusProvider : HostProvider {

    var lastSaved: Long? = null

    val filePath = System.getProperty("user.home") + "\\hostPlus"


    override fun provide(): List<HostRecord> {
        val file = File(filePath)
        if (!file.exists()) {
            throw FileNotFoundException("hostPlus file is not in ${filePath},pls check again")
        }
        val ans = mutableListOf<HostRecord>()
        if (lastSaved == null || file.lastModified() > lastSaved!!) {

            file.useLines {
                it.forEach {
                    val split = it.trim().split(" +".toRegex())
                    if (split.size == 2) {
                        ans.add(
                            HostRecord(
                                sourceHost = split[0].split(":")[0],
                                sourcePort = if (split[0].split(":").size == 1) 80 else split[0].split(":")[1].toInt(),
                                targetHost = split[1].split(":")[0],
                                targetPort = if (split[1].split(":").size == 1) 80 else split[1].split(":")[1].toInt()

                            )
                        )
                    }
                }
            }
        }
        lastSaved = file.lastModified()
        return ans

    }


}


object HostHolder {

    private val holder = ConcurrentHashMap<String, HostRecord>()


    private val hostKey = { sourceHost: String, sourcePort: Int -> "${sourceHost}:${sourcePort}" }


    fun getHostRecord(sourceHost: String, sourcePort: Int): HostRecord? = holder[hostKey(sourceHost, sourcePort)]

    fun saveHostRecord(hostRecord: HostRecord) {
        holder[hostKey(hostRecord.sourceHost, hostRecord.sourcePort)] = hostRecord
    }

    fun clear() = holder.clear()


}


