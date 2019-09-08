import os
import sys
import requests
import subprocess

def getLatestCommitHash(baseUrl):
    response = requests.get(baseUrl + '/latest_commit_hash')
    if response.status_code != 200:
        print("Error while trying to get latest commit hash from the server" +
              ", response status = " + str(response.status_code) +
              ", message = " + str(response.content))
        exit(-1)

    return response.content.decode("utf-8")


def uploadApk(baseUrl, headers, latestCommits):
    apkPath = "Kuroba\\app\\build\\outputs\\apk\\debug\\Kuroba.apk"
    inFile = open(apkPath, "rb")
    try:
        if not inFile.readable():
            print("Provided file is not readable, path = " + str(apkPath))
            exit(-1)

        print(latestCommits)

        response = requests.post(
            baseUrl + '/upload',
            files=dict(apk=inFile, latest_commits=latestCommits),
            headers=headers)

        if response.status_code != 200:
            print("Error while trying to upload file" +
                  ", response status = " + str(response.status_code) +
                  ", message = " + str(response.content))
            exit(-1)

        print("Successfully uploaded")
    except Exception as e:
        print("Unhandled exception: " + str(e))
        exit(-1)
    finally:
        inFile.close()


def getLatestCommitsFrom(latestCommitHash):
    if len(latestCommitHash) <= 0:
        print("Latest commit hash is empty, should be okay")

    arguments = ['gradlew', '-Pfrom=' + latestCommitHash + ' getLastCommits']

    result = subprocess.run(arguments, stdout=subprocess.PIPE)
    resultText = str(result.stdout)

    print("getLastCommits result: " + resultText)
    return resultText


if __name__ == '__main__':
    args = len(sys.argv)
    if args != 4:
        print("Bad arguments count, should be 4 got " + str(args))
        exit(-1)

    headers = dict(SECRET_KEY=sys.argv[1], APK_VERSION=sys.argv[2])
    baseUrl = sys.argv[3]
    latestCommitHash = ""

    try:
        latestCommitHash = getLatestCommitHash(baseUrl)
    except Exception as e:
        print("Couldn't get latest commit hash from the server: " + str(e))
        exit(-1)

    latestCommits = getLatestCommitsFrom(latestCommitHash)

    if len(latestCommits) <= 0:
        print("latestCommits is empty, nothing was commited to the project since last build so do nothing, "
              "latestCommitHash = " + latestCommitHash)
        exit(0)

    uploadApk(baseUrl, headers, latestCommits)
    exit(0)
