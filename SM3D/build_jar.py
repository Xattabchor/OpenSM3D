#!/usr/bin/env python3
"""Упаковка игрового JAR: классы + ресурсы + манифест из готового JAR.

Используется build.sh. Кладёт в архив только то, что нужно игре:
code/**, gamedata/**, objects/**, d, icon.png, META-INF/MANIFEST.MF.
Классы javax.microedition.* / org.microemu.* / android.* не попадают в архив.
"""

import argparse
import glob
import os
import zipfile

FILE_ATTR = 0o100644 << 16
DIR_ATTR = 0o40755 << 16
FIXED_DATE = (2026, 1, 1, 0, 0, 0)


def add_dir(zf, name):
    info = zipfile.ZipInfo(name, date_time=FIXED_DATE)
    info.compress_type = zipfile.ZIP_STORED
    info.external_attr = DIR_ATTR
    zf.writestr(info, b'')


def add_file(zf, name, data):
    info = zipfile.ZipInfo(name, date_time=FIXED_DATE)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = FILE_ATTR
    zf.writestr(info, data)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--classes', required=True, help='каталог с code/*.class')
    parser.add_argument('--res', required=True, help='каталог ресурсов SM3D/res')
    parser.add_argument('--manifest', required=True,
                        help='JAR-образец, из которого берётся META-INF/MANIFEST.MF')
    parser.add_argument('--out', required=True)
    args = parser.parse_args()

    with zipfile.ZipFile(args.manifest) as sample:
        manifest = sample.read('META-INF/MANIFEST.MF')

    with zipfile.ZipFile(args.out, 'w', zipfile.ZIP_DEFLATED) as zf:
        add_dir(zf, 'META-INF/')
        add_file(zf, 'META-INF/MANIFEST.MF', manifest)
        add_dir(zf, 'code/')
        for path in sorted(glob.glob(os.path.join(args.classes, '*.class'))):
            with open(path, 'rb') as fh:
                add_file(zf, 'code/' + os.path.basename(path), fh.read())

        for root, dirs, files in os.walk(args.res):
            dirs.sort()
            files.sort()
            rel = os.path.relpath(root, args.res)
            if rel != '.':
                add_dir(zf, rel.replace(os.sep, '/') + '/')
            for name in files:
                arc = (rel.replace(os.sep, '/') + '/' if rel != '.' else '') + name
                with open(os.path.join(root, name), 'rb') as fh:
                    add_file(zf, arc, fh.read())

    print('собран', args.out, os.path.getsize(args.out), 'байт')


if __name__ == '__main__':
    main()
