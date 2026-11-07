set -eu
echo "-------------------------------------------------------------------------------------------------------------------"
echo "Installing Fake Origin"
echo "-------------------------------------------------------------------------------------------------------------------"

cd /opt/fake-origin
echo `pwd`

# Install the versions recorded in the service lockfile.
npm ci

chmod 755 /opt/fake-origin
