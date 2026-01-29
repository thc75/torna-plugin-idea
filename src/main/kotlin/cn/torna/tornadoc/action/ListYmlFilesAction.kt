package cn.torna.tornadoc.action

import cn.torna.plugin.core.ProjectContext
import cn.torna.plugin.core.TornaPlugin
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import java.util.function.Consumer

/**
 * 查询当前项目中的 torna yml 文件并作为子菜单显示
 *
 * @author Torna
 */
internal class ListYmlFilesAction : DefaultActionGroup() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        // 只有当当前文件是 Java 文件时才显示菜单
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val isJavaFile = virtualFile?.extension == "java"
        e.presentation.isEnabledAndVisible = isJavaFile
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val project = e?.getData(CommonDataKeys.PROJECT) ?: return emptyArray()
        val baseDir = project.baseDir ?: return emptyArray()

        // 查找所有 yml 和 yaml 文件
        val ymlFiles = mutableListOf<VirtualFile>()
        collectYmlFiles(baseDir, ymlFiles)

        // 创建子菜单项
        return ymlFiles.map { file ->
            YmlFileAction(file)
        }.toTypedArray()
    }

    private fun collectYmlFiles(dir: VirtualFile, result: MutableList<VirtualFile>) {
        dir.children.forEach { child ->
            when {
                child.isDirectory -> {
                    // 递归查找子目录，排除常见的构建目录
                    if (child.name !in setOf("node_modules", ".gradle", "target", "build", "out", ".idea")) {
                        collectYmlFiles(child, result)
                    }
                }

                child.extension in setOf("yml", "yaml") && child.name.startsWith("torna") -> {
                    result.add(child)
                }
            }
        }
    }

    /**
     * 单个 yml 文件的子菜单 Action
     */
    private class YmlFileAction(private val file: VirtualFile) : AnAction(file.name) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

        override fun actionPerformed(e: AnActionEvent) {
//            println("Selected yml file: ${file.name}") // 选择的 yml 文件名
            val ymlPath = file.path
            val basePath = e.project?.basePath ?: ""
            println("Yml path: $ymlPath") // 完整路径
            println("Project path: $basePath")

            // 获取当前编辑器和 PSI 文件
            val editor = e.getData(CommonDataKeys.EDITOR)
            val psiFile = e.getData(CommonDataKeys.PSI_FILE)

            if (editor != null && psiFile != null) {
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
                            val method = qualifiedName + "." + method.name
                            println("Method: $method")
                            pushDoc(ymlPath, basePath, "method:$method")
                        }
                    } else {
                        // 不在方法内部，查找类
                        val psiClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
                        if (psiClass != null) {
                            val qualifiedName = psiClass.qualifiedName
                            println("Class: $qualifiedName")
                            pushDoc(ymlPath, basePath, "class:$qualifiedName")
                        }
                    }
                }
            }
        }

        private fun pushDoc(config: String, srcDir: String, scan: String) {
            TornaPlugin.pushDoc(config, Consumer { ctx: ProjectContext? ->
                val pluginConfig = ctx!!.pluginConfig
                pluginConfig.srcDirs = mutableListOf<String?>(srcDir)
                pluginConfig.scans = mutableListOf<String?>(scan)
            })
        }
    }


}
