"""Compile an independent Java addon against the packaged SDK and cached Paper libraries."""
import argparse
import os
import subprocess
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', required=True, type=Path)
    parser.add_argument('--paper-libraries', required=True, type=Path)
    parser.add_argument('--sdk', required=True, type=Path)
    parser.add_argument('--source', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    classes = args.output.parent / 'addon-classes'
    classes.mkdir(parents=True, exist_ok=True)
    sources = sorted((args.source / 'src/main/java').rglob('*.java'))
    libraries = sorted(args.paper_libraries.rglob('*.jar'))
    assert sources and libraries and args.sdk.is_file(), 'SDK, cached Paper libraries and addon source required'
    classpath = os.pathsep.join(str(p.resolve()) for p in [args.sdk, *libraries])
    command = args.output.parent / 'addon-javac.args'
    def quote(value):
        return '"' + str(value).replace('\\', '/') + '"'
    command.write_text('\n'.join(['--release', '25', '-classpath', quote(classpath), '-d',
                                  quote(classes.resolve()), *(quote(p.resolve()) for p in sources)]), encoding='utf-8')
    subprocess.run([str(args.java_home / 'bin/javac'), '@' + str(command.resolve())], check=True)
    subprocess.run([str(args.java_home / 'bin/jar'), '--create', '--file', str(args.output.resolve()),
                    '-C', str(classes.resolve()), '.', '-C', str((args.source / 'src/main/resources').resolve()), '.'], check=True)
    print('Independent addon compiled:', args.output.name)


if __name__ == '__main__':
    main()
