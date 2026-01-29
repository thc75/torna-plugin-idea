package cn.torna.tornadoc.action

import cn.torna.plugin.core.ProjectContext
import cn.torna.plugin.core.TornaPlugin
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.util.PsiTreeUtil
import java.util.function.Consumer

/**
 * Yml 文件操作的工具类
 *
 * @author Torna
 */
internal object YmlFileHelper {
    val excludeDirs = setOf("node_modules", "target", "build", "out")

    /**
     * 收集项目中的所有 torna yml 文件
     */
    fun collectYmlFiles(dir: VirtualFile, result: MutableList<VirtualFile>) {
        val path = dir.path
        dir.children.forEach { child ->
            when {
                child.isDirectory -> {
                    // 递归查找子目录，排除常见的构建目录
                    if (!child.name.startsWith(".") && child.name !in excludeDirs) {
                        collectYmlFiles(child, result)
                    }
                }

                child.extension in setOf("yml", "yaml") && child.name.startsWith("torna") -> {
                    result.add(child)
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

        pushDoc(ymlPath, basePath, scan)
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

//    private fun pushDoc(psiFile: PsiFile) {
//        println("PsiFile: $psiFile")
////        val psiClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
////        if (psiClass != null) {
////            val qualifiedName = psiClass.qualifiedName
////            println("Class: $qualifiedName")
////            pushDoc(ymlPath, basePath, "class:$qualifiedName")
////        }
//    }

    private fun pushDoc(config: String, srcDir: String, scan: String?) {
        if (scan == null || scan.isEmpty()) {
            return
        }
        TornaPlugin.pushDoc(config, Consumer { ctx: ProjectContext? ->
            val pluginConfig = ctx!!.pluginConfig
            pluginConfig.srcDirs = mutableListOf<String?>(srcDir)
            pluginConfig.scans = mutableListOf<String?>(scan)
        })
    }
}
