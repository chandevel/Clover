import os
import sys
import requests
import subprocess

testCommits = """6019ab13ed8dc814ffabbdc76902eb07e2332f30 - Dmitry, 6 weeks ago : Update README.md
a03149610f28aa241c8e06fe2614645a1a11724d - Dmitry, 6 weeks ago : Merge pull request #21 from K1rakishou/dev
abbf9ade1fad68373ab91638ac1e8c598327bda5 - k1rakishou, 7 weeks ago : Trigger CI build
725f752a1f902926c29b504ad0306411ef6bf20c - k1rakishou, 7 weeks ago : Trigger CI build
568197ca26f4916b9213df41d95232dc38bd8719 - k1rakishou, 7 weeks ago : Merge remote-tracking branch 'origin/multi-feature' into multi-feature
c12c6863d3f0ff01b5b7ea971157a54ce1c9e24e - k1rakishou, 7 weeks ago : CI apk uploading
ce880e86b32244c2396b30cf96218c012b82a625 - k1rakishou, 7 weeks ago : Introduce travis CI
1604d021b04f1e428fd2ced9d1b4d61bf27086db - Dmitry, 7 weeks ago : Update README.md
48ca131cfe189c3701b2581d8d4a9ba6ef9cf2a4 - Dmitry, 7 weeks ago : Update README.md
72270df91cd47932a0003c477060036c09e9da36 - Dmitry, 7 weeks ago : Update README.md
3fb60638f572a5062ca1c092e652287ee0abda72 - Dmitry, 7 weeks ago : Update README.md
a59c48553af80a4517827e9a730d8e946ad5b3c0 - Dmitry, 7 weeks ago : Update README.md

"""


def getLatestCommitHash():
    response = requests.get('http://127.0.0.1:8080/latest_commit_hash')
    if response.status_code != 200:
        print("Error while trying to get latest commit hash from the server" +
              ", response status = " + str(response.status_code) +
              ", message = " + str(response.content))
        exit(-1)

    return response.content.decode("utf-8")


def uploadApk(headers, projectBaseDir, latestCommits):
    inFile = open(projectBaseDir + "\\app\\build\\outputs\\apk\\debug\\Kuroba.apk", "rb")
    try:
        if not inFile.readable():
            print("Provided file is not readable, path = " + str(projectBaseDir))
            exit(-1)

        print(latestCommits)

        response = requests.post(
            'http://127.0.0.1:8080/upload',
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


def getLatestCommitsFrom(projectBaseDir, latestCommitHash):
    if (len(latestCommitHash) <= 0):
        print("Latest commit hash is empty, should be okay")

    gradlewFileName = "gradlew"

    # FIXME: doesn't work on windows
    if (os.name == 'nt'):
        return testCommits

    # TODO: rename getCheckedOutGitCommitHash
    arguments = [projectBaseDir + '\\' + gradlewFileName, '-Pfrom=' + latestCommitHash + ' getCheckedOutGitCommitHash']

    result = subprocess.run(arguments, stdout=subprocess.PIPE)
    stdoutText = str(result.stdout)

    print("text = " + stdoutText)
    return stdoutText


if __name__ == '__main__':
    # fXylnrM1UKQ3IKRmYRTPtYK3U0k0Icl3Z1cOakqr6JidAmfXwR1DY2ORyHV6Ggk10vkHT30cDrZsKX9zn0hpWIdAnuN6FQKfOXlbcTullzbusG8v2I5lbFSql7v1Ttf7 401010 F:\\projects\\android\\forked\\Kuroba\\Kuroba\\app\\build\\outputs\\apk\\debug\\Kuroba.apk

    # git log ${from}^..HEAD --pretty=format:\"%H - %an, %ar : %s\" --first-parent
    # Run gradle task with commit parameter -> gradlew -Pfrom=a03149610f28aa241c8e06fe2614645a1a11724d getCheckedOutGitCommitHash

    args = len(sys.argv)
    if args != 4:
        print("Bad arguments count, should be 4 got " + str(args))
        exit(-1)

    print("secretKey = " + str(sys.argv[1]))
    print("apkVersion = " + str(sys.argv[2]))
    print("projectBaseDir = " + str(sys.argv[3]))

    headers = dict(SECRET_KEY=sys.argv[1], APK_VERSION=sys.argv[2])
    projectBaseDir = sys.argv[3]

    latestCommitHash = getLatestCommitHash()
    latestCommits = getLatestCommitsFrom(projectBaseDir, latestCommitHash)

    if len(latestCommits) <= 0:
        print("latestCommits is empty, nothing was commited to the project since last build so do nothing, "
              "latestCommitHash = " + latestCommitHash)
        exit(0)

    uploadApk(headers, projectBaseDir, latestCommits)
    exit(0)
