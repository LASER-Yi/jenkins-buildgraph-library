# jenkins-buildgraph-library

A Jenkins shared library for running the build graph in any unreal project.

[TOC]

# Library Features

- Convert Build Graph to Jenkins Pipeline
- Automatically post UGS badges when a specific stage is finished
- Automatically dispatch stage to all available nodes
- And more...

## Supported Build Graph Features

The following build graph features are implemented by this library

- [x] Options
- [x] Agent-Based Parallel Build (Agents will be mapped to Jenkins **Nodes**)
- [x] Sequential Build
- [x] Agent Types (Types will be mapped to Jenkins Node **Labels**)
- [x] UGS Badges
- [x] Run Early
- [ ] Notify

# Setup

## Library Dependencies

The following plugins should be installed before using this library

- Pipeline
- Pipeline Utility Steps

The following plugins are recommended to use with this library

- Pipeline Graph View

## Shared Library

To use this library, you must add it to the Jenkins instance first

Add this library in **Manage Jenkins** -> **Configure System** -> **Global Pipeline Libraries**

**Load implicitly** should be enabled. Otherwise, you need to import the library every time you want to use it.

If you cannot find the section, please install [Pipeline: Groovy Libraries](https://plugins.jenkins.io/pipeline-groovy-lib/) first

## Jenkins Instance

- **BUILDGRAPH_SHARED_DIR** Location of build intermediates. Should be a shared directory and every node must have Read/Write access to it.
- (Optional) **BUILDGRAPH_METADATA_SERVER** The metadata server endpoint to post the UGS badge status.
- **IsBuildMachine** environment variable must be set to **1** to enable build machine specific tasks

## Jenkins Nodes

Jenkins nodes must be configured with the following options

- The node must contain **BuildGraph** label to run the task.
- **BUILDGRAPH_WORKSPACE_DIR** Location of the workspace root. (grouped by stream name)
- (Optional) **UE-LocalDataCachePath** Use the same local DDC directory for all workspaces to increase performance

# Usage

## Pipeline

Use this library in a **scripted pipeline**

```groovy

buildGraph('<build-graph-name-ended-with-xml>') { // example: Engine/Scripts/Graph/BuildAndCook.xml
    branch = '<branch-name>' // example: //epic/dev

    scm = { branch, changelist ->
        // the scm provider to checkout the workspace
    }

    target = '<build-graph-target>'

    // The option argument, can be set locally by -set:<OptionName>=<option-value>
    options = {
        OptionName = '<option-value>'
    }
}

```

Use this library in a **declarative pipeline**

```groovy

pipeline {
    agent none // must be none

    options {
        // ...
    }

    stages {
        stage('Run') {
            steps {
                script {
                    buildGraph('<build-graph-name-ended-with-xml>') {
                        // options...
                    }
                }
            }
        }
    }
}

```

Use this library but run everything in sequence

```groovy

buildGraphSequential('<build-graph-name-ended-with-xml>') { // example: Engine/Scripts/Graph/BuildAndCook.xml
    // options...
}

```

## Options

The following options can be set inside the buildGraph section.

**branch** The branch to execute the build graph.

**scm** Return the SCM provider the project is using to checkout the workspace, _branch_ and _changelist_ parameters must be used if you want to run in parallel.

**target** the target of the build graph.

**options** the options of the build graph.

**executable** Optional, custom the location of the Unreal Automation Tool, default to `Engine/Build/BatchFiles/RunUAT.bat`.

**param** Optional, additional parameters that will pass to UAT.

**preview** Optional, enable preview mode (all stages except Generate will be skipped).

**nocheckout** Optional, disable checkout feature.

**nobadge** Optional, disable badge feature.

## Built-in Perforce SCM

If your project is using Perforce as the VCS, please use the built-in Perforce implementation in the library. Otherwise, the SCM feature might break when you want to upgrade to the latest version

To use built-in perforce SCM, import the following module in your `Jenkinsfile`

```groovy
import com.laseryi.buildgraph.Perforce

buildGraph('<build-graph-name-ended-with-xml>') { // example: Engine/Scripts/Graph/BuildAndCook.xml
    branch = '<branch-name>' // example: //epic/dev

    scm = Perforce.scm('<your-p4-credential>')

    // options...
}

```

To customize the built-in perforce SCM, use the following approach

```groovy
import com.laseryi.buildgraph.Perforce

buildGraph('<build-graph-name-ended-with-xml>') { // example: Engine/Scripts/Graph/BuildAndCook.xml
    branch = '<branch-name>' // example: //epic/dev

    scm = Perforce.scm('<your-p4-credential>') {
        it['filter'] = Perforce.codeFilter() // Use built-in polling filter, useful for UGS relative tasks
    }

    // options...
}
```

> Important notice
>
> The built-in P4 scm will create the workspace under BUILDGRAPH_WORKSPACE_DIR
>
> The name will be `jenkins-${NODE_NAME}-${EXECUTOR_NUMBER}-${ESCAPED_STREAM_NAME}-buildgraph`

## Multiple Executors

You can enable multiple executors in your Jenkins node to fully utilize the CPU and I/O performance. For example, compile the code and cook the content in parallel

## Example

### Multibranch project

```groovy

String stream = "//<your-p4-stream>/${BRANCH_NAME}"

buildGraph('<build-graph-name-ended-with-xml>') {
    branch = stream

    // Other options
    //...
}


```

### Setup the build properties

```groovy

properties([
    pipelineTriggers([cron('H 1 * * 1-5')]) // 1am every working days
])

buildGraph('<build-graph-name-ended-with-xml>') {
    // Other options
    //...
}

```

### With Environment Variables, Credentials, and Post Actions

```groovy

pipeline {
    // agent must set to 'none' in here
    agent none

    environment {
        // env, creds...
    }

    stages {
        stage('Run') {
            steps {
                script {
                    buildGraph('<build-graph-name-ended-with-xml>') {
                        // options...
                    }
                }
            }
        }
    }

    post {
        cleanup {
            echo "cleaning..."
        }
    }
}

```

### Post Badges to UGS

To post badges to UGS, you need to define `Badge` elements in your BuildGraph XML

```xml
<Badge name="BP" requires="$(SomeRandomNodeName)" Change="$(Change)" Project="//depot/branch/folder" />
```

This library will find the PostBadgeStatus.exe executable in the Engine folder and connect to the metadata server defined by **BUILDGRAPH_METADATA_SERVER**

### RunEarly

You can use `RunEarly` flag to mark a node running immediately when all dependencies are satisfied

```xml
<Node Name="Some Random Node Name" RunEarly="true">
    <!-- Node Steps -->
</Node>
```

> Noted that this flag shouldn't be enabled when executing on a Jenkins instance with few executors
> Since we are taking up a executor but still wait for another tasks to be finished. Doing so might create a deadlock
