package cn.rainafter.app.gazekeep

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform