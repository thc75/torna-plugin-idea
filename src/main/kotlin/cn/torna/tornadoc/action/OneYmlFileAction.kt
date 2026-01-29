package cn.torna.tornadoc.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile

/**
 * 当只有一个 yml 文件时，直接执行操作
 *
 * @author Torna
 */
internal class OneYmlFileAction : AnAction("Push Doc") {
    private var ymlFile: VirtualFile? = null

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        // 检查选中的是 Java 文件还是 package 目录
        val isSelectedJavaOrPackage = YmlFileHelper.isSelectedJavaFileOrPackage(e)

        // 检查是否有 yml 文件
        val project = e.getData(CommonDataKeys.PROJECT)
        val baseDir = project?.baseDir

        if (baseDir != null && isSelectedJavaOrPackage) {
            // 查找所有 yml 文件
            val ymlFiles = mutableListOf<VirtualFile>()
            YmlFileHelper.collectYmlFiles(baseDir, ymlFiles)

            // 只有一个文件时才显示此 action
            if (ymlFiles.size == 1) {
                ymlFile = ymlFiles[0]
                e.presentation.isEnabledAndVisible = true
            } else {
                ymlFile = null
                e.presentation.isEnabledAndVisible = false
            }
        } else {
            ymlFile = null
            e.presentation.isEnabledAndVisible = false
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val file = ymlFile ?: return
        YmlFileHelper.executePushDoc(e, file)
    }


    private fun hasJavaFileRecursively(directory: PsiDirectory): Boolean {
        // 检查当前目录的文件
        if (directory.files.any { it is PsiJavaFile }) {
            return true
        }
        // 递归检查子目录
        return directory.subdirectories.any { hasJavaFileRecursively(it) }
    }
}
