
require 'fileutils'

TESTING_MODE="TESTING_MODE"
ARTIFACTS_MODE="ARTIFACTS_MODE"

def buildCode() 
  build_result = %x( ./gradlew clean && ./gradlew build )
  # check if the build worked 
  if ($?.exitstatus != 0) 
    puts "Build error, aborting"
    exit($?.exitstatus)
  end
end

def buildTestingMode()
  build(TESTING_MODE)
end

def buildArtifactsMode()
  build(ARTIFACTS_MODE)
end

def build(mode) 
  # make sure we are in the correct place
  assertPresentInCurrentDirectory(["settings.gradle"])

  gradleFiles = ["CBLite/build.gradle", 
                 "CBLiteEktorp/build.gradle", 
                 "CBLiteListener/build.gradle",
                 "CouchbaseLiteProject/build.gradle"]

  # backup original file build.gradle files
  backupFiles(gradleFiles)
  
  if mode == TESTING_MODE
    setTestingMode(gradleFiles)
  elsif mode == ARTIFACTS_MODE
    setArtifactsMode(gradleFiles)
  end

  # build the code
  puts "Building .."
  build_result = buildCode()
  puts "Build result: #{build_result}"

  # restore original files
  restoreFiles(gradleFiles)

end

def assertPresentInCurrentDirectory(file_list) 

  Dir.foreach('.') do |item|
    next if item == '.' or item == '..'
    if file_list.include? item 
      file_list.delete item
    end
  end

  raise "Did not find all %s in current dir" % file_list if file_list.size() != 0

end

def backupFiles(file_list)
  file_list.each do |src| 
    dest = "#{src}.bak"
    puts "Copying #{src} to #{dest}"
    FileUtils.cp(src, dest)
  end
end

def setTestingMode(file_list)
  # change occurrences of dependencies-archive.gradle -> dependencies-test.gradle
  file_list.each do |src| 
    puts "Set #{src} to testing mode"
    outdata = File.read(src).gsub(/dependencies-archive.gradle/, "dependencies-test.gradle")
    File.open(src, 'w') do |out|
      out << outdata
    end 
  end
end

def setArtifactsMode(file_list)
  # change occurrences of dependencies-test.gradle -> dependencies-archive.gradle
  file_list.each do |src| 
    puts "Set #{src} to archive mode"
    outdata = File.read(src).gsub(/dependencies-test.gradle/, "dependencies-archive.gradle")
    File.open(src, 'w') do |out|
      out << outdata
    end 
  end
end

def restoreFiles(file_list)
  file_list.each do |dest| 
    src = "#{dest}.bak"
    puts "Restoring #{src} to #{dest}"
    FileUtils.cp(src, dest)
    FileUtils.remove(src)
  end
end


