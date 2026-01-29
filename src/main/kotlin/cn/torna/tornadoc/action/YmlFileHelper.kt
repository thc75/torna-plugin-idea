package cn.torna.tornadoc.action

import cn.torna.plugin.core.ProjectContext
import cn.torna.plugin.core.PushListener
import cn.torna.plugin.core.TornaPlugin
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import java.lang.Exception

/**
 * Yml 文件操作的工具类
 *
 * @author Torna
 */
internal object YmlFileHelper {
    val excludeDirs = setOf("node_modules", "target", "build", "out")

    /**
     * 收集项目中的所有 torna 开头的 yml 文件（支持多模块项目）
     */
    fun collectYmlFiles(dir: VirtualFile, result: MutableList<VirtualFile>) {
        val path = dir.path

        // 只在 src/main/resources 目录下查找
        if (path.contains("/src/main/resources")) {
            dir.children.forEach { child ->
                when {
                    child.isDirectory -> {
                        // 排除以 . 开头的目录和构建目录
                        if (!child.name.startsWith(".") && child.name !in excludeDirs) {
                            collectYmlFiles(child, result)
                        }
                    }
                    child.extension in setOf("yml", "yaml") && child.name.startsWith("torna") -> {
                        result.add(child)
                    }
                }
            }
            return
        }

        // 不在 src/main/resources 目录下，递归查找子模块中的 src 目录
        dir.children.forEach { child ->
            if (child.isDirectory) {
                // 排除以 . 开头的目录和常见的构建/依赖目录
                if (!child.name.startsWith(".") && child.name !in excludeDirs) {
                    collectYmlFiles(child, result)
                }
            }
        }
    }

    fun isSelectedJavaFileOrPackage(e: AnActionEvent): Boolean {
        // 检查是否是 Java 文件
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (virtualFile?.extension == "java") {
            return true
        }

        // 检查是否是 package 目录
        val psiFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        return psiFile != null && psiFile.path.contains("/src/main/java")
    }

    /**
     * 执行 Push Doc 操作
     */
    fun executePushDoc(e: AnActionEvent, file: VirtualFile) {
        val ymlPath = file.path
        val basePath = e.project?.basePath ?: ""
        println("Yml path: $ymlPath")
        println("Project path: $basePath")

        // 获取当前编辑器和 PSI 文件
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        var scan: String? = null
        if (psiFile is PsiJavaFile) {
            // 编辑器右键
            if (editor != null) {
                scan = getScanStr(psiFile, editor)
            } else {
                // Java类右键
                val packageName = psiFile.getPackageName()
                val fileName = psiFile.virtualFile?.name
                // 移除文件后缀名
                val className = fileName?.substring(0, fileName.length - 5)
                scan = "class:${packageName}.${className}"
            }
        } else {
            // package右键
            val path = e.getData(CommonDataKeys.VIRTUAL_FILE)?.path ?: ""
            // 截取 src/main/java/ 后面部分，
            // 转换为包路径，例如 src/main/java/com/example/demo/ -> com.example.demo
            val searchPath = "src/main/java/"
            val index: Int = path?.indexOf(searchPath) ?: -1
            if (index != -1) {
                val packagePath = path?.substring(index + searchPath.length) ?: ""
                val packageName = packagePath.replace("/", ".")
                scan = "package:$packageName"
            }
        }

        println("scan: $scan")

        pushDoc(e.project, ymlPath, basePath, scan)
    }

    private fun getScanStr(psiFile: PsiFile, editor: Editor): String? {
        // 获取光标位置的元素
        val offset = editor.caretModel.offset
        val element: PsiElement? = psiFile.findElementAt(offset)

        if (element != null) {
            // 查找包含当前元素的方法
            val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)

            if (method != null) {
                // 在方法内部，获取包含该方法的类
                val containingClass = method.containingClass
                if (containingClass != null) {
                    val qualifiedName = containingClass.qualifiedName
                    val methodStr = "$qualifiedName.${method.name}"
                    val methodScan = "method:$methodStr"
                    return methodScan
                }
            } else {
                // 不在方法内部，查找类
                val psiClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
                if (psiClass != null) {
                    val qualifiedName = psiClass.qualifiedName
                    val classScan = "class:$qualifiedName"
                    return classScan
                }
            }
        }
        return null
    }

    private fun pushDoc(project: Project?, config: String, srcDir: String, scan: String?) {
        if (scan == null || scan.isEmpty()) {
            return
        }

        TornaPlugin.pushDoc(config, object : PushListener {
            override fun onBeforePush(projectContext: ProjectContext?) {
                val pluginConfig = projectContext!!.pluginConfig
                pluginConfig.srcDirs = mutableListOf<String?>(srcDir)
                pluginConfig.scans = mutableListOf<String?>(scan)
            }

            override fun onError(projectContext: ProjectContext?, e: Exception?) {
                project?.let {
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("torna.doc.notification.group")
                        .createNotification(
                            "Push doc failed",
                            e?.message ?: "Unknown error",
                            NotificationType.ERROR
                        )
                        .notify(it)
                }
            }

            override fun onAfterPush(projectContext: ProjectContext?, result: String?) {
                val objectMapper: ObjectMapper = ObjectMapper()
                val readValue = objectMapper.readValue<Map<String, Any?>>(result!!)

                // 判断是否成功 (code == 0 表示成功)
                val code = readValue["code"] as? String
                val isSuccess = code == "0"

                project?.let {
                    val notification = NotificationGroupManager.getInstance()
                        .getNotificationGroup("torna.doc.notification.group")
                    if (isSuccess) {
                        notification
                            .createNotification(
                                "Push doc success",
                                NotificationType.INFORMATION
                            )
                            .notify(it)
                    } else {
                        val message = readValue["msg"] as? String ?: "Push doc failed"
                        notification
                            .createNotification(
                                "Push doc failed",
                                message,
                                NotificationType.ERROR
                            )
                            .notify(it)
                    }
                }
            }
        })

    }
}
