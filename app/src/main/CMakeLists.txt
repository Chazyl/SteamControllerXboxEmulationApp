# Sets the minimum version of CMake required to build the native
# library. You should either keep the default value or only pass a
# value of 3.18.1 or higher.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name.
project("steamcontrollerxbox")

# Creates and names a library, sets it as STATIC or SHARED,
# and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
             uinput_wrapper

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             cpp/uinput_wrapper.c 
             
             # Enable C11 standard
             PROPERTIES
             C_STANDARD 11
             C_STANDARD_REQUIRED ON)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build steps.

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
                       uinput_wrapper

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib} )
