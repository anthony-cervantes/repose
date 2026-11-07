set -eu
echo "-------------------------------------------------------------------------------------------------------------------"
echo "Installing Fake Keystone"
echo "-------------------------------------------------------------------------------------------------------------------"

cd /opt/fake-keystone
echo `pwd`

# Install the versions recorded in the service lockfile.
npm ci

chmod 755 /opt/fake-keystone
