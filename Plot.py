import matplotlib.pyplot as plt
import sys

# file structure:
# Server type
# 3 constant parameters "paramName value"
# Changing parameter
# N measures "changingParameterValue avaregeTimeInMillis"

parameters = {}
changingParameter = ''
changingParameterValues = {}
results = {}

for filename in sys.argv[1:]:
    with open(filename, 'r') as file:
        type = file.readline().strip()
        results[type] = []
        changingParameterValues[type] = []
        for i in range(3):
            paramName, value = file.readline().strip().split(" ", 2)
            parameters[paramName] = value
        changingParameter = file.readline().strip()
        for measure in [x.strip() for x in file.readlines()]:
            changingParameterValue, time = measure.split(" ", 2)
            changingParameterValues[type].append(changingParameterValue)
            results[type].append(int(time))

for (k, v) in results.items():
    plt.plot(changingParameterValues[k], v, label=k)

plt.legend()

plt.xlabel(changingParameter)
plt.ylabel('Average time in milliseconds')
title = ''
for (k, v) in parameters.items():
    title = title + k + '=' + v + '\n'
plt.title(title)

plt.show()
