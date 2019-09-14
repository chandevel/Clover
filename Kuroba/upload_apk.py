import sys
import requests
import subprocess

def run(*popenargs, input=None, check=False, **kwargs):
    if input is not None:
        if 'stdin' in kwargs:
            raise ValueError('stdin and input arguments may not both be used.')
        kwargs['stdin'] = subprocess.PIPE

    process = subprocess.Popen(*popenargs, **kwargs)
    try:
        stdout, stderr = process.communicate(input)
    except:
        process.kill()
        process.wait()
        raise
    retcode = process.poll()
    if check and retcode:
        raise subprocess.CalledProcessError(
            retcode, process.args, output=stdout, stderr=stderr)
    return retcode, stdout, stderr

def getLatestCommitHash(baseUrl):
    response = requests.get(baseUrl + '/latest_commit_hash')
    if response.status_code != 200:
        print("getLatestCommitHash() Error while trying to get latest commit hash from the server" +
              ", response status = " + str(response.status_code) +
              ", message = " + str(response.content))
        exit(-1)

    return response.content.decode("utf-8")


def uploadApk(baseUrl, headers, latestCommits):
    apkPath = "Kuroba\\app\\build\\outputs\\apk\\debug\\Kuroba.apk"
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
    print("branchName = \"" + str(branchName) + "\", latestCommitHash = \"" + str(latestCommitHash) + "\"")

    arguments = ['gradlew',
                 '-Pfrom=' + latestCommitHash + ' -Pbranch_name=' + branchName + ' getLastCommitsFromCommitByHash']

    if len(latestCommitHash) <= 0:
        arguments = ['gradlew',
                     '-Pbranch_name=' + branchName + ' getLastTenCommits']

    print("getLatestCommitsFrom() arguments: " + str(arguments))

    retcode, stdout, _ = run(args=arguments, stdout=subprocess.PIPE)
    resultText = str(stdout)

    print("getLatestCommitsFrom() getLastCommits result: " + resultText + ", retcode = " + str(retcode))
    return resultText


if __name__ == '__main__':
    args = len(sys.argv)
    if args != 5:
        print("Bad arguments count, should be 5 got " + str(args) + ", expected arguments: "
                                                                    "\n1. Secret key, "
                                                                    "\n2. Apk version, "
                                                                    "\n3. Base url, "
                                                                    "\n4. Branch name")
        exit(-1)

    headers = dict(SECRET_KEY=sys.argv[1], APK_VERSION=sys.argv[2])
    baseUrl = sys.argv[3]
    branchName = sys.argv[4]
    latestCommitHash = ""

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
        print("main() latestCommits is empty, nothing was commited to the project since last build so do nothing, "
              "latestCommitHash = " + latestCommitHash)
        exit(0)

    uploadApk(baseUrl, headers, latestCommits)
    exit(0)
