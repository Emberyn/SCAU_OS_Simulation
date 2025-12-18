package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.filesystem.Directory;
import org.example.scau_os_simulation.filesystem.File;
import org.example.scau_os_simulation.filesystem.FileSystem;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * 文件系统管理器（FileSystemManager）
 * 核心职责：
 * 1. 封装文件系统的核心操作（创建/删除/复制粘贴文件/目录）
 * 2. 管理磁盘空间分配与释放，保证操作的原子性和空间准确性
 */
public class FileSystemManager
{
    // 底层文件系统实例（负责磁盘空间的分配/释放核心逻辑）
    private final FileSystem fileSystem;
    // 文件系统根目录（所有文件/目录的顶级父节点）
    private final Directory rootDirectory;

    // 文件系统变更监听器列表
    // 用于通知UI层（如资源管理器）文件系统发生变化，需要刷新视图
    private final List<Runnable> listeners = new ArrayList<>();

    /**
     * 构造方法 - 初始化文件系统管理器
     * @param fileSystem 底层文件系统实例（提供磁盘空间管理能力）
     */
    public FileSystemManager(FileSystem fileSystem)
    {
        this.fileSystem = fileSystem;
        this.rootDirectory = new Directory("root"); // 初始化根目录

        // 初始化系统基础目录（确保/system和/user目录存在）
        if (findDirectoryByPath("/system") == null) rootDirectory.addChild(new Directory("system"));
        if (findDirectoryByPath("/user") == null) rootDirectory.addChild(new Directory("user"));

        // 获取/system目录，用于创建内核核心文件
        Directory systemDir = findDirectoryByPath("/system");

        // 创建内核文件kernel.sys（大小128字节），仅当文件不存在时创建
        if (systemDir.findChild("kernel.sys") == null) {
            File kernelFile = new File("kernel.sys", 128);
            systemDir.addChild(kernelFile);
            fileSystem.allocateSpace(128); // 分配磁盘空间（修复点：创建文件必须扣空间）
        }

        // 创建/system/exec目录（存放可执行文件），仅当目录不存在时创建
        if (systemDir.findChild("exec") == null) {
            systemDir.addChild(new Directory("exec"));
        }
    }

    // 监听器相关方法（UI刷新核心） ---
    /**
     * 注册文件系统变更监听器
     * @param listener 监听器回调（通常是UI层的刷新方法）
     */
    public void addListener(Runnable listener) {
        this.listeners.add(listener);
    }

    /**
     * 通知所有注册的监听器（文件系统已变更）
     * 内部调用，所有修改文件系统的操作成功后都会触发
     */
    private void notifyListeners() {
        // 遍历监听器并执行回调，捕获异常避免单个监听器失败影响整体
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (Exception e) {
                e.printStackTrace(); // 异常仅打印，不中断其他监听器
            }
        }
    }

    // --- 核心业务方法 ---
    /**
     * 粘贴（复制）文件或目录（核心修复方法）
     * @param source 待复制的源对象（File/Directory）
     * @param targetPath 目标目录路径（如"/user"）
     * @return 复制后的新对象（File/Directory），失败返回null
     */
    public synchronized Object paste(Object source, String targetPath)
    {
        // 查找目标目录，路径不存在则粘贴失败
        Directory targetDir = findDirectoryByPath(targetPath);
        if (targetDir == null) {
            System.err.println("粘贴失败：目标路径不存在 " + targetPath);
            return null;
        }

        Object result = null;
        // 1. 复制文件
        if (source instanceof File) {
            result = pasteFile((File) source, targetDir);
        }
        // 2. 复制目录（递归复制所有子文件/子目录）
        else if (source instanceof Directory) {
            result = pasteDirectoryRecursive((Directory) source, targetDir);
        }

        // 【新增】粘贴成功则通知UI刷新
        if (result != null) {
            notifyListeners();
        }
        return result;
    }

    /**
     * 粘贴文件的具体实现（深拷贝+空间校验）
     * @param srcFile 源文件
     * @param targetDir 目标目录
     * @return 新创建的文件对象，空间不足返回null
     */
    private File pasteFile(File srcFile, Directory targetDir) {
        // 获取源文件大小，用于分配空间
        int size = srcFile.getSize();

        // 校验磁盘空间，不足则拒绝复制
        if (!fileSystem.allocateSpace(size)) {
            System.err.println("错误：磁盘空间不足，无法复制文件: " + srcFile.getName());
            return null;
        }

        // 生成唯一文件名（避免目标目录已有同名文件）
        String uniqueName = getUniqueName(targetDir, srcFile.getName());
        // 创建新文件（深拷贝，新对象独立于源文件）
        File newFile = new File(uniqueName, size);

        // 深拷贝文件内容（避免引用同一字节数组）
        if (srcFile.getContent() != null) {
            byte[] srcData = srcFile.getContent();
            byte[] newData = new byte[srcData.length];
            System.arraycopy(srcData, 0, newData, 0, srcData.length); // 数组拷贝
            newFile.setContent(newData);
        }

        // 将新文件添加到目标目录
        targetDir.addChild(newFile);
        return newFile;
    }

    /**
     * 递归粘贴目录（包含所有子文件/子目录）
     * @param srcDir 源目录
     * @param targetDir 目标目录
     * @return 新创建的目录对象
     */
    private Directory pasteDirectoryRecursive(Directory srcDir, Directory targetDir) {
        // 生成唯一目录名（避免重名）
        String uniqueName = getUniqueName(targetDir, srcDir.getName());
        Directory newDir = new Directory(uniqueName);
        targetDir.addChild(newDir); // 将新目录添加到目标目录

        // 递归处理源目录的所有子节点
        for (Object child : srcDir.getChildren()) {
            if (child instanceof File) {
                // 子节点是文件：调用pasteFile复制
                pasteFile((File) child, newDir);
            } else if (child instanceof Directory) {
                // 子节点是目录：递归调用自身复制
                pasteDirectoryRecursive((Directory) child, newDir);
            }
        }
        return newDir;
    }

    /**
     * 生成唯一名称（文件/目录），避免父目录下重名
     * @param parent 父目录
     * @param originalName 原始名称
     * @return 唯一名称
     */
    private String getUniqueName(Directory parent, String originalName) {
        // 原始名称未被占用，直接返回
        if (parent.findChild(originalName) == null) return originalName;

        // 拆分文件名和扩展名（处理带后缀的文件）
        String baseName = originalName;
        String extension = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0) { // 确保扩展名存在（如file.txt，而非.file）
            baseName = originalName.substring(0, dotIndex);
            extension = originalName.substring(dotIndex);
        }

        // 循环生成带序号的名称，直到找到未被占用的名称
        int counter = 1;
        String newName;
        do {
            newName = baseName + "(" + counter + ")" + extension;
            counter++;
        } while (parent.findChild(newName) != null);
        return newName;
    }



    /**
     * 创建文件（带空间校验+UI通知）
     * @param path 父目录路径（如"/user"）
     * @param name 文件名
     * @param size 文件大小（字节）
     * @return 新创建的文件对象，失败返回null
     */
    public synchronized File createFile(String path, String name, int size) {
        // 查找父目录，路径不存在则创建失败
        Directory parent = findDirectoryByPath(path);
        if (parent == null) return null;

        // 校验并分配磁盘空间
        if (!fileSystem.allocateSpace(size)) {
            System.out.println("磁盘空间不足");
            return null;
        }

        // 生成唯一文件名，创建文件并添加到父目录
        String uniqueName = getUniqueName(parent, name);
        File newFile = new File(uniqueName, size);
        parent.addChild(newFile);

        // 通知UI刷新
        notifyListeners();
        return newFile;
    }


    /**
     * 创建目录（带唯一名称+UI通知）
     * @param path 父目录路径
     * @param name 目录名
     * @return 新创建的目录对象，失败返回null
     */
    public synchronized Directory createDirectory(String path, String name) {
        // 查找父目录，路径不存在则创建失败
        Directory parent = findDirectoryByPath(path);
        if (parent == null) return null;

        // 生成唯一目录名，创建目录并添加到父目录
        String uniqueName = getUniqueName(parent, name);
        Directory newDir = new Directory(uniqueName);
        parent.addChild(newDir);

        // 通知UI刷新
        notifyListeners();
        return newDir;
    }




    /**
     * 这里可以优化
     * 删除指定路径的文件/目录（核心删除方法）
     * @param path 要删除的路径（如"/user/test.txt"或"/system/temp"）
     * @return true=删除成功，false=删除失败（路径不存在/根目录/权限问题）
     */
    public synchronized boolean deletePath(String path) {
        // 禁止删除根目录或空路径
        if (path.equals("/") || path.isEmpty()) return false;

        // 拆分父路径和目标名称（如"/user/test.txt" → 父路径"/user"，名称"test.txt"）
        String parentPath = path.substring(0, path.lastIndexOf('/'));
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (parentPath.isEmpty()) parentPath = "/"; // 处理根目录下的文件（如"/kernel.sys" → 父路径"/"）


        // 查找父目录和目标对象
        Directory parent = findDirectoryByPath(parentPath);
        if (parent == null) return false;
        Object target = parent.findChild(name);
        if (target == null) return false;


        boolean success = false;
        // 1. 删除文件：释放空间并移除节点
        if (target instanceof File) {
            File f = (File) target;
            parent.removeChild(f);
            fileSystem.freeSpace(f.getSize()); // 释放磁盘空间
            success = true;
        }
        // 2. 删除目录：递归删除所有子节点后移除目录本身
        else if (target instanceof Directory) {
            success = deleteDirectoryRecursive(parent, (Directory) target);
        }

        // 删除成功则通知UI刷新
        if (success) {
            notifyListeners();
        }
        return success;
    }

    /**
     * 递归删除目录（包含所有子文件/子目录）
     * @param parent 待删除目录的父目录
     * @param target 待删除的目录
     * @return true=删除成功，false=删除失败
     */
    private boolean deleteDirectoryRecursive(Directory parent, Directory target) {
        // 复制子节点列表（避免遍历过程中列表变更）
        List<Object> children = new ArrayList<>(target.getChildren());
        // 遍历并删除所有子节点
        for (Object child : children) {
            if (child instanceof File) {
                // 删除子文件：释放空间并移除
                target.removeChild(child);
                fileSystem.freeSpace(((File) child).getSize());
            } else if (child instanceof Directory) {
                // 递归删除子目录
                deleteDirectoryRecursive(target, (Directory) child);
            }
        }
        // 移除当前目录本身
        return parent.removeChild(target);
    }



    /**
     * 根据路径查找目录
     * @param path 目录路径（如"/system/exec"）
     * @return 目录对象，路径不存在/不是目录返回null
     */
    public Directory getDirectory(String path) {
        return findDirectoryByPath(path);
    }

    /**
     * 内部方法：根据路径递归查找目录（核心路径解析）
     * @param path 目录路径（支持绝对路径，如"/user/doc"）
     * @return 目录对象，路径不存在返回null
     */
    private Directory findDirectoryByPath(String path) {
        // 根路径直接返回根目录
        if (path.equals("/") || path.isEmpty()) return rootDirectory;

        // 拆分路径片段（如"/system/exec" → ["", "system", "exec"]）
        String[] parts = path.split("/");
        Directory current = rootDirectory;
        // 遍历路径片段查找目录
        for (String part : parts) {
            if (part.isEmpty()) continue; // 跳过空片段（split("/")会生成空字符串）
            boolean found = false;

            // 遍历当前目录的子节点，查找匹配名称的目录
            for (Object child : current.getChildren()) {
                if (child instanceof Directory d && d.getName().equals(part)) {
                    current = d;
                    found = true;
                    break;
                }
            }

            // 某个片段未找到，返回null
            if (!found) return null;
        }
        return current;
    }


    /**
     * 创建可执行文件（将指令列表写入文件）
     * @param path 父目录路径
     * @param name 可执行文件名
     * @param instructions 指令列表（每行一条指令）
     * @return 新创建的可执行文件对象，失败返回null
     */
    public synchronized File createExecutable(String path, String name, List<String> instructions) {
        // 计算文件大小：按64字节为单位向上取整（最小1字节）
        int size = Math.max(1, (instructions.size() + 63) / 64);
        // 调用createFile创建文件（内部已处理空间分配和UI通知）
        File f = createFile(path, name, size);
        if (f == null) return null;

        // 将指令列表拼接为字符串，写入文件内容
        StringBuilder sb = new StringBuilder();
        for (String s : instructions) sb.append(s).append("\n");
        f.setContent(sb.toString().getBytes(StandardCharsets.UTF_8));

        return f;
    }


    /**
     * 重载方法：从Executable对象创建可执行文件
     * @param path 父目录路径
     * @param name 可执行文件名
     * @param exec Executable对象（包含指令集）
     * @return 新创建的可执行文件对象
     */
    public synchronized File createExecutable(String path, String name, org.example.scau_os_simulation.process.Executable exec) {
        // 将Executable指令转换为字符串列表
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < exec.length(); i++) lines.add(exec.fetch(i));
        return createExecutable(path, name, lines);
    }

    /**
     * 加载可执行文件（从文件解析指令集）
     * @param path 可执行文件路径
     * @return Executable对象（包含指令集），文件不存在/无内容返回空指令集
     */
    public synchronized org.example.scau_os_simulation.process.Executable loadExecutable(String path) {
        // 查找并读取文件
        File f = getFileByPath(path);
        if (f == null) return null;
        if (f.getContent() == null) return new org.example.scau_os_simulation.process.Executable(new ArrayList<>());

        // 将文件内容转换为指令列表（按换行拆分）
        String content = new String(f.getContent(), StandardCharsets.UTF_8);
        List<String> lines = Arrays.asList(content.split("\n"));
        return new org.example.scau_os_simulation.process.Executable(lines);
    }

    /**
     * 根据路径查找文件
     * @param path 文件路径（如"/system/exec/test.exe"）
     * @return 文件对象，路径不存在/不是文件返回null
     */
    public synchronized File getFileByPath(String path) {
        // 拆分父路径和文件名
        String parentPath = path.substring(0, path.lastIndexOf('/'));
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        if (parentPath.isEmpty()) parentPath = "/"; // 处理根目录下的文件

        // 查找父目录和目标文件
        Directory dir = findDirectoryByPath(parentPath);
        if (dir == null) return null;

        Object child = dir.findChild(fileName);
        return (child instanceof File) ? (File) child : null;
    }

    /**
     * 根据路径查找任意对象（文件/目录）
     * @param path 目标路径
     * @return File/Directory对象，路径不存在返回null
     */
    public synchronized Object getObjectByPath(String path) {
        if (path == null || path.isEmpty()) return null;
        // 根路径返回根目录
        if (path.equals("/")) return rootDirectory;
        // 先尝试查找目录，找不到再尝试查找文件
        Directory dir = findDirectoryByPath(path);
        if (dir != null) return dir;
        return getFileByPath(path);
    }

    /**
     * 获取根目录（只读）
     * @return 根目录对象
     */
    public synchronized Directory getRootDirectory() { return rootDirectory; }

    /**
     * 获取底层文件系统实例
     * @return FileSystem对象
     */
    public synchronized FileSystem getFileSystem() { return fileSystem; }
}