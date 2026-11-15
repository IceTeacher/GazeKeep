package cn.rainafter.app.gazekeep.ui.text

/** 项目自身对外链接的唯一来源；第三方依赖链接随许可信息留在各自页面。 */
internal object ProjectLinks {
  const val REPOSITORY = "https://github.com/IceTeacher/GazeKeep"

  /** 置顶的问题反馈帖，内含面向普通用户的反馈模板；不直接打开 issues 列表。 */
  const val FEEDBACK = "$REPOSITORY/issues/24"
  const val WEBSITE = "https://gazekeep.rainafter.cn"
}
