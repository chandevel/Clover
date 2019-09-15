import sys
import requests
import subprocess
from pathlib import Path


def getApkVersionCode():
    gradlewFullPath = str(Path(__file__).parent.absolute()) + "/gradlew"
    arguments = [gradlewFullPath, 'getVersionCode', '-q']

    print("getApkVersionCode() arguments: " + str(arguments))
    stdout = subprocess.check_output(arguments)
    print("result = " + str(stdout))

    return str(stdout).strip()


def getLatestCommitHash(baseUrl):
    response = requests.get(baseUrl + '/latest_commit_hash')
    if response.status_code != 200:
        print("getLatestCommitHash() Error while trying to get latest commit hash from the server" +
              ", response status = " + str(response.status_code) +
              ", message = " + str(response.content))
        exit(-1)

    return response.content.decode("utf-8")


def uploadApk(baseUrl, headers, latestCommits):
    apkPath = "app/build/outputs/apk/dev/debug/null.apk"  # FIXME: change null to Kuroba when it works
    inFile = open(apkPath, "rb")
    try:
        if not inFile.readable():
            print("uploadApk() Provided file is not readable, path = " + str(apkPath))
            exit(-1)

        print(latestCommits)

        response = requests.post(
            baseUrl + '/upload',
            files=dict(apk=inFile, latest_commits=latestCommits),
            headers=headers)

        if response.status_code != 200:
            print("uploadApk() Error while trying to upload file" +
                  ", response status = " + str(response.status_code) +
                  ", message = " + str(response.content))
            exit(-1)

        print("uploadApk() Successfully uploaded")
    except Exception as e:
        print("uploadApk() Unhandled exception: " + str(e))
        exit(-1)
    finally:
        inFile.close()


def getLatestCommitsFrom(branchName, latestCommitHash):
    gradlewFullPath = str(Path(__file__).parent.absolute()) + "/gradlew"

    print("branchName = \"" + str(branchName) + "\", latestCommitHash = \"" + str(
        latestCommitHash) + "\", gradlewFullPath = \"" + gradlewFullPath + "\"")

    arguments = [gradlewFullPath,
                 '-Pfrom=' + latestCommitHash + ' -Pbranch_name=' + branchName,
                 'getLastCommitsFromCommitByHash',
                 '-q']

    if len(latestCommitHash) <= 0:
        arguments = [gradlewFullPath,
                     '-Pbranch_name=' + branchName,
                     'getLatestCommit',
                     '-q']

    print("getLatestCommitsFrom() arguments: " + str(arguments))
    stdout = subprocess.check_output(arguments)
    print("result = " + str(stdout))

    return str(stdout)


if __name__ == '__main__':
    # First argument is the script full path which we don't need
    args = len(sys.argv) - 1
    if args != 3:
        print("Bad arguments count, should be 3 got " + str(args) + ", expected arguments: "
                                                                    "\n1. Secret key, "
                                                                    "\n2. Base url, "
                                                                    "\n3. Branch name")
        exit(-1)

    secretKey = sys.argv[1]
    apkVersion = getApkVersionCode()
    baseUrl = sys.argv[2]
    branchName = sys.argv[3]
    latestCommitHash = ""

    if len(apkVersion) <= 0:
        print("main() Bad apk version code " + apkVersion)
        exit(-1)

    try:
        latestCommitHash = getLatestCommitHash(baseUrl)
    except Exception as e:
        print("Couldn't get latest commit hash from the server, error: " + str(e))
        exit(-1)

    latestCommits = ""

    try:
        latestCommits = getLatestCommitsFrom(branchName, latestCommitHash)
    except Exception as e:
        print("main() Couldn't get latest commits list from the gradle task, error: " + str(e))
        exit(-1)

    if len(latestCommits) <= 0:
        print("main() latestCommits is empty, nothing was committed to the project since last build so do nothing, "
              "latestCommitHash = " + latestCommitHash)
        exit(0)

    headers = dict(
        SECRET_KEY=secretKey,
        APK_VERSION=apkVersion)

    uploadApk(baseUrl, headers, latestCommits)
    exit(0)
