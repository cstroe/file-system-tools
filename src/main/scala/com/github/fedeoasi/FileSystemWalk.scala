package com.github.fedeoasi

import java.io.{File, FileInputStream}
import java.nio.file.Path

import com.github.fedeoasi.Model.{DirectoryEntry, FileSystemEntry, FileEntry}
import org.apache.commons.codec.digest.DigestUtils
import resource.managed

import scala.util.{Failure, Success, Try}

/** Walks the file system tree and gathers all the entries.
  *
  * Supports incremental walks by taking the `existingEntries` parameter.
  */
class FileSystemWalk(directory: Path, existingEntries: Seq[FileSystemEntry] = Seq.empty) {
  require(directory.toFile.isDirectory)

  private val entriesByPath = existingEntries.groupBy(_.path)

  def run(): Seq[FileSystemEntry] = {
    run(directory.toFile)
  }

  def run(file: File): Seq[FileSystemEntry] = {
    val dirEntry = createDirectory(file)
    val newEntries = file.listFiles().flatMap { child =>
      if (child.isDirectory) {
        run(child)
      } else {
        if (!entriesByPath.contains(child.getPath)) {
          createFile(child).toSeq
        } else {
          Seq.empty
        }
      }
    }
    if (!entriesByPath.contains(file.getPath)) {
      dirEntry +: newEntries
    } else {
      newEntries
    }
  }

  private def createDirectory(file: File): DirectoryEntry = {
    DirectoryEntry(file.getParent, file.getName)
  }

  private def createFile(file: File): Option[FileEntry] = {
    println(file.getPath)
    Try {
      managed(new FileInputStream(file)).acquireAndGet { fis =>
        val md5 = DigestUtils.md5Hex(fis)
        FileEntry(file.getParent, file.getName, md5, file.length())
      }
    } match {
      case Success(fileEntry) => Some(fileEntry)
      case Failure(_) =>
        println(s"Error processing file ${file.getPath}" )
        None
    }
  }
}
