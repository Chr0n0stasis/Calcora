cmake_minimum_required(VERSION 3.22)
project(calcora_ios LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

get_filename_component(REPOSITORY_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../.." ABSOLUTE)
set(GIAC_SOURCE_DIR "${REPOSITORY_ROOT}/giac-2.0.0")
set(GIAC_ORIGINAL_SRC "${GIAC_SOURCE_DIR}/src")
set(LIBTOMMATH_DIR "${REPOSITORY_ROOT}/third_party/libtommath-1.3.0")
set(GIAC_SRC "${CMAKE_CURRENT_BINARY_DIR}/giac-src")

# Work on a private source copy so configuring iOS never overwrites the
# Android build's generated giac/config.h in the repository.
file(COPY "${GIAC_ORIGINAL_SRC}/" DESTINATION "${GIAC_SRC}")

# Giac ships an iPhone config, but this project uses libtommath instead of the
# historical iOS GMP/intl combination.
file(READ "${GIAC_ORIGINAL_SRC}/config.h.iphone" GIAC_IOS_CONFIG)
string(REPLACE "#define ENABLE_NLS 1" "/* Calcora: no gettext/NLS */" GIAC_IOS_CONFIG "${GIAC_IOS_CONFIG}")
string(REPLACE "#define HAVE_LIBINTL 1" "/* Calcora: no libintl */" GIAC_IOS_CONFIG "${GIAC_IOS_CONFIG}")
string(REPLACE "/* #define HAVE_LIBGSL 1 */" "/* Calcora: no GSL */" GIAC_IOS_CONFIG "${GIAC_IOS_CONFIG}")
string(REPLACE "/* #undef HAVE_TOMMATH_H */" "#define HAVE_TOMMATH_H 1" GIAC_IOS_CONFIG "${GIAC_IOS_CONFIG}")
string(REPLACE "#define PACKAGE_VERSION \"0.9.8\"" "#define PACKAGE_VERSION \"2.0.0\"" GIAC_IOS_CONFIG "${GIAC_IOS_CONFIG}")
string(REPLACE "#define VERSION \"0.9.8\"" "#define VERSION \"2.0.0\"" GIAC_IOS_CONFIG "${GIAC_IOS_CONFIG}")
file(WRITE "${GIAC_SRC}/config.h" "${GIAC_IOS_CONFIG}")

set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
add_subdirectory("${LIBTOMMATH_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/libtommath")

# LibTomMath enables warnings from system headers, but its own Apple guard only
# covers CMAKE_SYSTEM_NAME=Darwin. iOS uses CMAKE_SYSTEM_NAME=iOS, which causes
# Apple SDK nullability diagnostics to flood the build log.
if(CMAKE_C_COMPILER_ID MATCHES "Clang")
    target_compile_options(libtommath PRIVATE
        -Wno-system-headers
        -Wno-nullability-completeness)
endif()

set(GIAC_CORE_SOURCES
    ${GIAC_SRC}/input_lexer.cc ${GIAC_SRC}/sym2poly.cc ${GIAC_SRC}/gausspol.cc
    ${GIAC_SRC}/threaded.cc ${GIAC_SRC}/moyal.cc ${GIAC_SRC}/maple.cc
    ${GIAC_SRC}/ti89.cc ${GIAC_SRC}/mathml.cc ${GIAC_SRC}/misc.cc
    ${GIAC_SRC}/permu.cc ${GIAC_SRC}/quater.cc ${GIAC_SRC}/desolve.cc
    ${GIAC_SRC}/input_parser.cc ${GIAC_SRC}/symbolic.cc ${GIAC_SRC}/index.cc
    ${GIAC_SRC}/modpoly.cc ${GIAC_SRC}/modfactor.cc ${GIAC_SRC}/ezgcd.cc
    ${GIAC_SRC}/derive.cc ${GIAC_SRC}/solve.cc ${GIAC_SRC}/intg.cc
    ${GIAC_SRC}/intgab.cc ${GIAC_SRC}/risch.cc ${GIAC_SRC}/lin.cc
    ${GIAC_SRC}/series.cc ${GIAC_SRC}/subst.cc ${GIAC_SRC}/vecteur.cc
    ${GIAC_SRC}/sparse.cc ${GIAC_SRC}/csturm.cc ${GIAC_SRC}/tex.cc
    ${GIAC_SRC}/global.cc ${GIAC_SRC}/ifactor.cc ${GIAC_SRC}/alg_ext.cc
    ${GIAC_SRC}/gauss.cc ${GIAC_SRC}/isom.cc ${GIAC_SRC}/help.cc
    ${GIAC_SRC}/plot.cc ${GIAC_SRC}/plot3d.cc ${GIAC_SRC}/rpn.cc
    ${GIAC_SRC}/prog.cc ${GIAC_SRC}/pari.cc ${GIAC_SRC}/cocoa.cc
    ${GIAC_SRC}/usual.cc ${GIAC_SRC}/identificateur.cc ${GIAC_SRC}/gen.cc
    ${GIAC_SRC}/unary.cc ${GIAC_SRC}/tinymt32.cc ${GIAC_SRC}/sha256.c
    ${GIAC_SRC}/first.cc)

add_library(giac_core STATIC ${GIAC_CORE_SOURCES})
target_include_directories(giac_core PRIVATE ${GIAC_SRC} ${GIAC_SOURCE_DIR} ${LIBTOMMATH_DIR})
target_compile_definitions(giac_core PRIVATE HAVE_CONFIG_H IN_GIAC IOS OSXIOS GIAC_GENERIC_CONSTANTS C11_UNORDERED_MAP)
target_compile_options(giac_core PRIVATE
    -O3 -fPIC -fno-strict-aliasing
    -Wno-deprecated -Wno-deprecated-declarations -Wno-unused-parameter
    -Wno-unused-variable -Wno-unused-result -Wno-parentheses -Wno-switch)

add_library(calcora_ios STATIC "${REPOSITORY_ROOT}/app/src/main/cpp/native-lib.cpp")
target_include_directories(calcora_ios PRIVATE ${GIAC_SRC} ${GIAC_SOURCE_DIR} ${LIBTOMMATH_DIR})
target_compile_definitions(calcora_ios PRIVATE
    CALCULATORPLUS_GIAC_SOURCE="${GIAC_SOURCE_DIR}"
    CALCULATORPLUS_WITH_GIAC=1 HAVE_CONFIG_H IN_GIAC IOS OSXIOS GIAC_GENERIC_CONSTANTS C11_UNORDERED_MAP)
target_compile_options(calcora_ios PRIVATE
    -O3 -fPIC -fno-strict-aliasing
    -Wno-unused-result)
target_link_libraries(giac_core PUBLIC libtommath)
target_link_libraries(calcora_ios PUBLIC giac_core libtommath)
