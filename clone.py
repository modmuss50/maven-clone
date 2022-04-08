from bs4 import BeautifulSoup
import urllib.request
import re
import os

validExts = ["jar", "pom", "asc", "md5", "sha256", "sha512", "sha1"]

def request(url: str) -> str:
    print("Requesting:" + url)
    with urllib.request.urlopen(url) as url:
        return url.read().decode()

def download(url: str, file: str):
    print("Downloading " + url)
    urllib.request.urlretrieve(url, file)

def clone(notation: str):
    print("Cloning: " + notation)
    baseUrl = "https://repo1.maven.org/maven2"

    group, artifact, version = notation.split(":")

    dirURL = "{}/{}/{}/{}".format(baseUrl, group.replace(".", "/"), artifact, version)
    dir = "{}/{}/{}".format(group.replace(".", "/"), artifact, version)

    anchors = findAnchors(dirURL + "/")

    for anchor in anchors:
        fileName = anchor.get('href')
        if isWantedFile(fileName) :
            downloadFile(dirURL, dir,  fileName)


def findAnchors(url: str):
    soup = BeautifulSoup(request(url), features="html.parser")
    return soup.findAll('a')

def isWantedFile(fileName: str) -> bool:
    ext = fileName.split(".")[-1]
    return ext in validExts

def downloadFile(dirUrl: str, dir: str, file: str):
    fileName = "files/" + dir + "/" + file

    if not os.path.isdir("files/" + dir):
        os.makedirs("files/" + dir)

    download(dirUrl + "/" + file, fileName)

def main():
    clone("org.ow2.asm:asm:9.3")
    clone("org.ow2.asm:asm-analysis:9.3")
    clone("org.ow2.asm:asm-commons:9.3")
    clone("org.ow2.asm:asm-tree:9.3")
    clone("org.ow2.asm:asm-util:9.3")
    clone("org.ow2.asm:asm-bom:9.3")

if __name__ == "__main__":
    main()