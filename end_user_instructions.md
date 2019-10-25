# Instructions for CSS434/533/534 Students

## Setup ssh keys and cluster access

1. Recieve username and password.

2. Login to one of:
     - ssh <USERNAME>@cssmpi1.uwb.edu
     - ssh <USERNAME>@cssmpi2.uwb.edu
     - ssh <USERNAME>@cssmpi3.uwb.edu
     - ssh <USERNAME>@cssmpi4.uwb.edu
     - ssh <USERNAME>@cssmpi5.uwb.edu
     - ssh <USERNAME>@cssmpi6.uwb.edu
     - ssh <USERNAME>@cssmpi7.uwb.edu
     - ssh <USERNAME>@cssmpi8.uwb.edu

3. Run setup script
     - ./setup_mpi_cluster.sh <OLD_PASSWORD> <NEW_PASSWORD>

     NOTE: <OLD_PASSWORD> is your original password
           <NEW_PASSWORD> must meet Linux security requirements, else
               you may need to rerun this script with a new password.
               If you are required to rerun the script, then you may

## You are now done

You can now log in to any of the eight machines using your <NEW_PASS>,
and ssh from any machine to any other machine!

Note: If you log into a new machine next time, then you will have to
      type "yes" into the terminal to add the ECDSA key fingerprint
      to the new host. This is normal behavior and needs to be done
      once for each host machine.

Linux/Mac users: Use `ssh-copy-id` to setup password-less login. See
                 `setup_mpi_cluster.sh` for an example of how to use
                 `ssh-keygen` to generate an SSH key

Username jlandron_css434
Password <mystandardPassword>